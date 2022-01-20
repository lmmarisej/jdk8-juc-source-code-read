package $04_ReadWriteLock;

/**
 * @author lmmarise.j@gmail.com
 * @since 2022/1/20 1:19 AM
 */
public class Main {
    public static void main(String[] args) {
        ReadWriteLockDemo readWriteLockDemo = new ReadWriteLockDemo();

        new Thread(new Runnable() {
            @Override
            public void run() {
                readWriteLockDemo.set((int) Math.random() * 101);
            }
        }, "Write: ").start();

        for (int i = 0; i < 100; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    readWriteLockDemo.get();
                }
            }, "read: ").start();
        }
    }
}
