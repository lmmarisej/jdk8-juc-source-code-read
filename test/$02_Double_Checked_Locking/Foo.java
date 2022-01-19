package $02_Double_Checked_Locking;

/**
 * @author lmmarise.j@gmail.com
 * @since 2022/1/19 1:36 PM
 */
public class Foo {
    private volatile Object instance;
    public Object getInstance() {
        if (instance == null) {               // 为了性能，延迟使用synchronized
            synchronized (this) {           // 为了一致性和内存可见性，必须使用synchronized
                if (instance == null) {
                    instance = new Object();
                }
            }
        }
        return instance;
    }
}
