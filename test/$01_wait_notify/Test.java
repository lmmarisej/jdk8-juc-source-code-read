package $01_wait_notify;

import lombok.SneakyThrows;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author lmmarise.j@gmail.com
 * @since 2022/1/18 9:18 PM
 */
public class Test {
    private static final int CAPACITY = 5;

    public static void main(String args[]) {
        // 利用queue对象，完成线程间通信
        Queue<Integer> queue = new LinkedList<>();

        Producer producer1 = new Producer(queue, CAPACITY);
        Consumer consumer1 = new Consumer(queue, CAPACITY);

        Thread p1 = new Thread(new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                while (true) {
                    producer1.callProduce();
                }
            }
        });
        Thread p2 = new Thread(new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                while (true) {
                    producer1.callProduce();
                }
            }
        });
        Thread c1 = new Thread(new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                while (true) {
                    consumer1.callConsumer();
                }
            }
        });
        p1.setName("P1");
        p2.setName("P2");
        c1.setName("C1");
        p1.start();
        p2.start();
        c1.start();
    }
}
