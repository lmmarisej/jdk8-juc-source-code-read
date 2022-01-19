package $01_wait_notify;

import java.util.Queue;
import java.util.Random;

/**
 * @author lmmarise.j@gmail.com
 * @since 2022/1/18 9:17 PM
 */
public class Consumer {
    private Queue<Integer> queue;
    int maxSize;

    public Consumer(Queue<Integer> queue, int maxSize) {
        this.queue = queue;
        this.maxSize = maxSize;
    }

    public void callConsumer() throws InterruptedException {
        synchronized (queue) {
            while (queue.isEmpty()) {
                System.out.println("Queue is empty, [" + Thread.currentThread().getName() + "] thread is waiting.");
                queue.wait();
            }
            int x = queue.poll();
            System.out.println("[" + Thread.currentThread().getName() + "] Consuming value : " + x);
            queue.notifyAll();

            try {
                Thread.sleep(new Random().nextInt(1000));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
