package $05_volatile;

/**
 * @author lmmarise.j@gmail.com
 * @since 2022/1/22 3:55 PM
 */
public class Main {
    static volatile int a = 10;

    public static void main(String[] args) {
        new Thread(() -> {
            for (; ; ) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(a);
            }
        }).start();
        new Thread(() -> {
            int b = a;
            for (; ; ) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(b);
            }
        }).start();

        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            a = 11;
        }).start();

        // 综上，volatile不能在方法中对局部变量使用，volatile只保证对象的成员变量的内存可见性，不保证线程栈中变量的可见性（线程私有）
    }
}
