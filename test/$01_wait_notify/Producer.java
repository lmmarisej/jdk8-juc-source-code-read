package $01_wait_notify;

import java.util.Queue;
import java.util.Random;

/**
 * @author lmmarise.j@gmail.com
 * @since 2022/1/18 9:17 PM
 */
public class Producer {
    private Queue<Integer> queue;
    int maxSize;
    int i = 0;

    public Producer(Queue<Integer> queue, int maxSize) {
        this.queue = queue;
        this.maxSize = maxSize;
    }

    public void callProduce() throws InterruptedException {
        synchronized (queue) {
            while (queue.size() == maxSize) {
                System.out.println("Queue is full, [" + Thread.currentThread().getName() + "] thread waiting.");
                queue.wait();
            }
            System.out.println("[" + Thread.currentThread().getName() + "] Producing value : " + i);
            queue.offer(i++);
            queue.notifyAll();
            try {
                Thread.sleep(new Random().nextInt(1000));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
