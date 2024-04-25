# hashCodeって実際どうやってるのを調べるための旅

JVMの実装を調べる  
AmazonCoretto8のGithubでコードを辿ってみた  
[corretto-8](https://github.com/corretto/corretto-8)


## Object.java見てみる
まずはObject.java  
[JavaDoc](https://docs.oracle.com/javase/jp/8/docs/api/java/lang/Object.html#hashCode--)
他のStringとかBooleanとかそれぞれで実装されてるので、今回は省略  

[corretto-8/jdk/src/share/classes/java/lang /Object.java](https://github.com/corretto/corretto-8/blob/10c3e5427539c2d60b3247229a862c249e17e6c5/jdk/src/share/classes/java/lang/Object.java)
```java
public native int hashCode();
```


ネイティブ修飾子なので、JavaではなくJVMの実装のもよう

#  hashCodeの宣言探す
コード検索したらすぐ見つかった。  
JVM_IHashCodeという処理みたい。  
[corretto-8/jdk/src/share/native/java/lang /Object.c](https://github.com/corretto/corretto-8/blob/10c3e5427539c2d60b3247229a862c249e17e6c5/jdk/src/share/native/java/lang/Object.c#L43)
```c
static JNINativeMethod methods[] = {
    {"hashCode",    "()I",                    (void *)&JVM_IHashCode},
    {"wait",        "(J)V",                   (void *)&JVM_MonitorWait},
    {"notify",      "()V",                    (void *)&JVM_MonitorNotify},
    {"notifyAll",   "()V",                    (void *)&JVM_MonitorNotifyAll},
    {"clone",       "()Ljava/lang/Object;",   (void *)&JVM_Clone},
};
```


## JVM_IHashCodeを探す
コード検索したらすぐ見つかった。  
ObjectSynchronizer::FastHashCodeという処理みたい  
[corretto-8/hotspot/src/share/vm/prims /jvm.cpp](https://github.com/corretto/corretto-8/blob/10c3e5427539c2d60b3247229a862c249e17e6c5/hotspot/src/share/vm/prims/jvm.cpp#L561)
```cpp
JVM_ENTRY(jint, JVM_IHashCode(JNIEnv* env, jobject handle))
  JVMWrapper("JVM_IHashCode");
  // as implemented in the classic virtual machine; return 0 if object is NULL
  return handle == NULL ? 0 : ObjectSynchronizer::FastHashCode (THREAD, JNIHandles::resolve_non_null(handle)) ;
JVM_END
```


## ObjectSynchronizer::FastHashCodを探す
コード検索したらすぐ見つかった。  
長い！！！  
とりあえず、hash値の計算は **get_next_hash** という関数みたいなのはわかった  

[corretto-8/hotspot/src/share/vm/runtime /synchronizer.cpp](https://github.com/corretto/corretto-8/blob/10c3e5427539c2d60b3247229a862c249e17e6c5/hotspot/src/share/vm/runtime/synchronizer.cpp#L626)
```cpp
intptr_t ObjectSynchronizer::FastHashCode (Thread * Self, oop obj) {
  if (UseBiasedLocking) {
    // NOTE: many places throughout the JVM do not expect a safepoint
    // to be taken here, in particular most operations on perm gen
    // objects. However, we only ever bias Java instances and all of
    // the call sites of identity_hash that might revoke biases have
    // been checked to make sure they can handle a safepoint. The
    // added check of the bias pattern is to avoid useless calls to
    // thread-local storage.
    if (obj->mark()->has_bias_pattern()) {
      // Box and unbox the raw reference just in case we cause a STW safepoint.
      Handle hobj (Self, obj) ;
      // Relaxing assertion for bug 6320749.
      assert (Universe::verify_in_progress() ||
              !SafepointSynchronize::is_at_safepoint(),
             "biases should not be seen by VM thread here");
      BiasedLocking::revoke_and_rebias(hobj, false, JavaThread::current());
      obj = hobj() ;
      assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
    }
  }

  // hashCode() is a heap mutator ...
  // Relaxing assertion for bug 6320749.
  assert (Universe::verify_in_progress() ||
          !SafepointSynchronize::is_at_safepoint(), "invariant") ;
  assert (Universe::verify_in_progress() ||
          Self->is_Java_thread() , "invariant") ;
  assert (Universe::verify_in_progress() ||
         ((JavaThread *)Self)->thread_state() != _thread_blocked, "invariant") ;

  ObjectMonitor* monitor = NULL;
  markOop temp, test;
  intptr_t hash;
  markOop mark = ReadStableMark (obj);

  // object should remain ineligible for biased locking
  assert (!mark->has_bias_pattern(), "invariant") ;

  if (mark->is_neutral()) {
    hash = mark->hash();              // this is a normal header
    if (hash) {                       // if it has hash, just return it
      return hash;
    }
    hash = get_next_hash(Self, obj);  // allocate a new hash code
    temp = mark->copy_set_hash(hash); // merge the hash code into header
    // use (machine word version) atomic operation to install the hash
    test = (markOop) Atomic::cmpxchg_ptr(temp, obj->mark_addr(), mark);
    if (test == mark) {
      return hash;
    }
    // If atomic operation failed, we must inflate the header
    // into heavy weight monitor. We could add more code here
    // for fast path, but it does not worth the complexity.
  } else if (mark->has_monitor()) {
    monitor = mark->monitor();
    temp = monitor->header();
    assert (temp->is_neutral(), "invariant") ;
    hash = temp->hash();
    if (hash) {
      return hash;
    }
    // Skip to the following code to reduce code size
  } else if (Self->is_lock_owned((address)mark->locker())) {
    temp = mark->displaced_mark_helper(); // this is a lightweight monitor owned
    assert (temp->is_neutral(), "invariant") ;
    hash = temp->hash();              // by current thread, check if the displaced
    if (hash) {                       // header contains hash code
      return hash;
    }
    // WARNING:
    //   The displaced header is strictly immutable.
    // It can NOT be changed in ANY cases. So we have
    // to inflate the header into heavyweight monitor
    // even the current thread owns the lock. The reason
    // is the BasicLock (stack slot) will be asynchronously
    // read by other threads during the inflate() function.
    // Any change to stack may not propagate to other threads
    // correctly.
  }

  // Inflate the monitor to set hash code
  monitor = ObjectSynchronizer::inflate(Self, obj, inflate_cause_hash_code);
  // Load displaced header and check it has hash code
  mark = monitor->header();
  assert (mark->is_neutral(), "invariant") ;
  hash = mark->hash();
  if (hash == 0) {
    hash = get_next_hash(Self, obj);
    temp = mark->copy_set_hash(hash); // merge hash code into header
    assert (temp->is_neutral(), "invariant") ;
    test = (markOop) Atomic::cmpxchg_ptr(temp, monitor, mark);
    if (test != mark) {
      // The only update to the header in the monitor (outside GC)
      // is install the hash code. If someone add new usage of
      // displaced header, please update this code
      hash = test->hash();
      assert (test->is_neutral(), "invariant") ;
      assert (hash != 0, "Trivial unexpected object/monitor header usage.");
    }
  }
  // We finally get the hash
  return hash;
}
```
## get_next_hashを見てみる
hashCode というパラメーターの設定によって分岐するみたい  

【hashCode == 0】  
 システムの乱数生成器（os::random()）を使用してハッシュコードを生成します。  
この方法は、複数のスレッドが同時に同じ値を生成する可能性があります。  

【hashCode == 1】  
オブジェクトのアドレスをベースにハッシュコードを生成し、停止-世界（Stop-The-World）操作の間も一貫性（idempotency）を保つことができます。これは同期スキームで有用です。  

【hashCode == 2】  
すべてのオブジェクトに対して同じ値1をハッシュコードとして割り当てます。  
主に感度テスト用みたい。  

【hashCode == 3】   
グローバルシーケンス（GVars.hcSequence）から次の値をインクリメントして使用します。これにより、連続するユニークなハッシュコードが生成されます。  

【hashCode == 4】   
オブジェクトのアドレスそのものをハッシュコードとして使用します。  

【その他】  
 Marsagliaのxor-shift方式を使用してハッシュコードを生成します。この方法はスレッドごとに独自の状態を持ち、良好な全体的な性能を提供するため、将来的にデフォルトの実装として選択される可能性が高いです。

[corretto-8/hotspot/src/share/vm/runtime /synchronizer.cpp](https://github.com/corretto/corretto-8/blob/10c3e5427539c2d60b3247229a862c249e17e6c5/hotspot/src/share/vm/runtime/synchronizer.cpp#L626)
```cpp
static inline intptr_t get_next_hash(Thread * Self, oop obj) {
  intptr_t value = 0 ;
  if (hashCode == 0) {
     // This form uses an unguarded global Park-Miller RNG,
     // so it's possible for two threads to race and generate the same RNG.
     // On MP system we'll have lots of RW access to a global, so the
     // mechanism induces lots of coherency traffic.
     value = os::random() ;
  } else
  if (hashCode == 1) {
     // This variation has the property of being stable (idempotent)
     // between STW operations.  This can be useful in some of the 1-0
     // synchronization schemes.
     intptr_t addrBits = cast_from_oop<intptr_t>(obj) >> 3 ;
     value = addrBits ^ (addrBits >> 5) ^ GVars.stwRandom ;
  } else
  if (hashCode == 2) {
     value = 1 ;            // for sensitivity testing
  } else
  if (hashCode == 3) {
     value = ++GVars.hcSequence ;
  } else
  if (hashCode == 4) {
     value = cast_from_oop<intptr_t>(obj) ;
  } else {
     // Marsaglia's xor-shift scheme with thread-specific state
     // This is probably the best overall implementation -- we'll
     // likely make this the default in future releases.
     unsigned t = Self->_hashStateX ;
     t ^= (t << 11) ;
     Self->_hashStateX = Self->_hashStateY ;
     Self->_hashStateY = Self->_hashStateZ ;
     Self->_hashStateZ = Self->_hashStateW ;
     unsigned v = Self->_hashStateW ;
     v = (v ^ (v >> 19)) ^ (t ^ (t >> 8)) ;
     Self->_hashStateW = v ;
     value = v ;
  }

  value &= markOopDesc::hash_mask;
  if (value == 0) value = 0xBAD ;
  assert (value != markOopDesc::no_hash, "invariant") ;
  TEVENT (hashCode: GENERATE) ;
  return value;
}
```

## hashCodeのデフォルト値を探す
検索してたら発見  
[corretto-8/hotspot/src/share/vm/runtime /globals.hpp](https://github.com/corretto/corretto-8/blob/10c3e5427539c2d60b3247229a862c249e17e6c5/hotspot/src/share/vm/runtime/globals.hpp#L4)
```hpp
  product(intx, hashCode, 5,                                                
          "(Unstable) select hashCode generation algorithm")    
```
デフォルトは5（その他）らしい。  
Unstableって昔のコメントがそのまま残り続けてるっぽい
