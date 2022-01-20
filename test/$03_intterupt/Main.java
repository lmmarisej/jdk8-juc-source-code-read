package $03_intterupt;

/**
 * @author lmmarise.j@gmail.com
 * @since 2022/1/20 12:12 AM
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {
        Thread.currentThread().interrupt();
//        synchronized (Main.class) {
//            Thread.sleep(0);
//            System.out.println("");
//        }
        Thread.sleep(0);
    }
}
