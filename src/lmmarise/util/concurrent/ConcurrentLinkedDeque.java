/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea and Martin Buchholz with assistance from members of
 * JCP JSR-166 Expert Group and released to the public domain, as explained
 * at http://creativecommons.org/publicdomain/zero/1.0/
 */

package lmmarise.util.concurrent;

import java.util.*;
import java.util.function.Consumer;

/**
 * An unbounded concurrent {@linkplain Deque deque} based on linked nodes.
 * Concurrent insertion, removal, and access operations execute safely
 * across multiple threads.
 * A {@code ConcurrentLinkedDeque} is an appropriate choice when
 * many threads will share access to a common collection.
 * Like most other concurrent collection implementations, this class
 * does not permit the use of {@code null} elements.
 * <p>
 * 双向链表结构的无界并发队列。不允许存储null值。
 *
 * <p>Iterators and spliterators are
 * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
 *
 * <p>Beware that, unlike in most collections, the {@code size} method
 * is <em>NOT</em> a constant-time operation. Because of the
 * asynchronous nature of these deques, determining the current number
 * of elements requires a traversal of the elements, and so may report
 * inaccurate results if this collection is modified during traversal.
 * Additionally, the bulk operations {@code addAll},
 * {@code removeAll}, {@code retainAll}, {@code containsAll},
 * {@code equals}, and {@code toArray} are <em>not</em> guaranteed
 * to be performed atomically. For example, an iterator operating
 * concurrently with an {@code addAll} operation might view only some
 * of the added elements.
 *
 * <p>This class and its iterator implement all of the <em>optional</em>
 * methods of the {@link Deque} and {@link Iterator} interfaces.
 *
 * <p>Memory consistency effects: As with other concurrent collections,
 * actions in a thread prior to placing an object into a
 * {@code ConcurrentLinkedDeque}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions subsequent to the access or removal of that element from
 * the {@code ConcurrentLinkedDeque} in another thread.
 * <p>
 * 内存一致性：对ConcurrentLinkedDeque的插入操作先行发生于(happen-before) 访问或移除操作。
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <E> the type of elements held in this collection
 * @author Doug Lea
 * @author Martin Buchholz
 * @since 1.7
 */
public class ConcurrentLinkedDeque<E>
        extends AbstractCollection<E>
        implements Deque<E>, java.io.Serializable {

    /*
     * This is an implementation of a concurrent lock-free deque
     * supporting interior removes but not interior insertions, as
     * required to support the entire Deque interface.
     *
     * We extend the techniques developed for ConcurrentLinkedQueue and
     * LinkedTransferQueue (see the internal docs for those classes).
     * Understanding the ConcurrentLinkedQueue implementation is a
     * prerequisite for understanding the implementation of this class.
     *
     * The data structure is a symmetrical doubly-linked "GC-robust"
     * linked list of nodes.  We minimize the number of volatile writes
     * using two techniques: advancing multiple hops with a single CAS
     * and mixing volatile and non-volatile writes of the same memory
     * locations.
     * 我们使用两种技术将volatile写的数量最小化:
     * 使用单个CAS操作推进多个跳跃点，并将volatile和非volatile写入相同的内存位置。
     *
     * A node contains the expected E ("item") and links to predecessor
     * ("prev") and successor ("next") nodes:
     *
     * class Node<E> { volatile Node<E> prev, next; volatile E item; }
     *
     * A node p is considered "live" if it contains a non-null item
     * (p.item != null).  When an item is CASed to null, the item is
     * atomically logically deleted from the collection.
     * 如果节点的item不为null，那么它就是一个live节点。
     * 当一个节点item被cas修改为null，那么这个节点相当于从链表中被删除
     *
     * At any time, there is precisely one "first" node with a null
     * prev reference that terminates any chain of prev references
     * starting at a live node.  Similarly there is precisely one
     * "last" node terminating any chain of next references starting at
     * a live node.  The "first" and "last" nodes may or may not be live.
     * The "first" and "last" nodes are always mutually reachable.
     * 在任何时候，"first"节点都会有一个空的prev引用，终止任何从live节点开始的prev引用链。
     * 类似地，"last"节点也会终止任何从live节点开始的next引用链。
     * "first"和"last"节点可能是live节点，也可能不是。
     * "first"和"last"节点总是相互可达的。
     *
     * A new element is added atomically by CASing the null prev or
     * next reference in the first or last node to a fresh node
     * containing the element.  The element's node atomically becomes
     * "live" at that point.
     * 一个新的元素被添加到链表中的first或last节点后，包含这个元素的节点就是一个live节点。
     *
     * A node is considered "active" if it is a live node, or the
     * first or last node.  Active nodes cannot be unlinked.
     * 如果一个节点是live节点或first/last节点，那么它也是一个active节点。
     * active节点不会被解除链接。
     *
     * A "self-link" is a next or prev reference that is the same node:
     *   p.prev == p  or  p.next == p
     * Self-links are used in the node unlinking process.  Active nodes
     * never have self-links.
     * “自链”节点：next或prev引用指向自身
     * 自链节点用在解除节点链接时，Active节点不会是自链节点。
     *
     * A node p is active if and only if:
     *
     * p.item != null ||
     * (p.prev == null && p.next != p) ||
     * (p.next == null && p.prev != p)
     *
     * The deque object has two node references, "head" and "tail".
     * The head and tail are only approximations to the first and last
     * nodes of the deque.  The first node can always be found by
     * following prev pointers from head; likewise for tail.  However,
     * it is permissible for head and tail to be referring to deleted
     * nodes that have been unlinked and so may not be reachable from
     * any live node.
     * ConcurrentLinkedDeque 内部有两个节点引用：head和tail。
     * head/tail也可能不是first/last节点。
     * 从head节点通过prev引用总是可以找到first节点，从tail节点通过next引用总是可以找到last节点。
     * 允许head和tail引用已删除的节点，这些节点已经被解除链接，因此可能无法从live节点访问到。
     *
     * There are 3 stages of node deletion;
     * "logical deletion", "unlinking", and "gc-unlinking".
     * 删除的3个阶段：逻辑删除->未链接->GC未链接
     *
     * 1. "logical deletion" by CASing item to null atomically removes
     * the element from the collection, and makes the containing node
     * eligible for unlinking.
     * 逻辑删除：通过CAS修改节点item为null来完成。表示当前节点可以被解除链接。
     *
     * 2. "unlinking" makes a deleted node unreachable from active
     * nodes, and thus eventually reclaimable by GC.  Unlinked nodes
     * may remain reachable indefinitely from an iterator.
     * 未链接：使节点从active节点不可达，最终被GC回收。已经解除链接的节点也可能在迭代器中可以访问到。
     *
     * Physical node unlinking is merely an optimization (albeit a
     * critical one), and so can be performed at our convenience.  At
     * any time, the set of live nodes maintained by prev and next
     * links are identical, that is, the live nodes found via next
     * links from the first node is equal to the elements found via
     * prev links from the last node.  However, this is not true for
     * nodes that have already been logically deleted - such nodes may
     * be reachable in one direction only.
     * 解除节点链接仅仅是一个优化方式，在我们方便的时候也可以这样做。
     * 在任何时候，从first通过next找到的live节点和从last通过prev找到的节点是相等的。
     * 但是，在节点被逻辑删除时上述结论不成立，这些被逻辑删除的节点也可能从一端是可达的。
     *
     * 3. "gc-unlinking" takes unlinking further by making active
     * nodes unreachable from deleted nodes, making it easier for the
     * GC to reclaim future deleted nodes.  This step makes the data
     * structure "gc-robust", as first described in detail by Boehm
     * (http://portal.acm.org/citation.cfm?doid=503272.503282).
     * GC未链接使已经被解除链接的节点从active不可达，使GC更容易回收被删除的节点。
     * 这一步是为了使数据结构保持GC健壮性(gc-robust)，防止保守垃圾收集器对这些边界空间的使用（只需要了解，后面会专门开篇讲解）。
     *
     * GC-unlinked nodes may remain reachable indefinitely from an
     * iterator, but unlike unlinked nodes, are never reachable from
     * head or tail.
     * GC未链接节点可能被iterator访问到，但是从head或tail访问不可达。
     *
     * Making the data structure GC-robust will eliminate the risk of
     * unbounded memory retention with conservative GCs and is likely
     * to improve performance with generational GCs.
     * 对保守的GC来说，使数据结构保持GC健壮性会消除内存无限滞留的问题，同时也提高了分代收机器的性能。
     *
     * When a node is dequeued at either end, e.g. via poll(), we would
     * like to break any references from the node to active nodes.  We
     * develop further the use of self-links that was very effective in
     * other concurrent collection classes.  The idea is to replace
     * prev and next pointers with special values that are interpreted
     * to mean off-the-list-at-one-end.  These are approximations, but
     * good enough to preserve the properties we want in our
     * traversals, e.g. we guarantee that a traversal will never visit
     * the same element twice, but we don't guarantee whether a
     * traversal that runs out of elements will be able to see more
     * elements later after enqueues at that end.  Doing gc-unlinking
     * safely is particularly tricky, since any node can be in use
     * indefinitely (for example by an iterator).  We must ensure that
     * the nodes pointed at by head/tail never get gc-unlinked, since
     * head/tail are needed to get "back on track" by other nodes that
     * are gc-unlinked.  gc-unlinking accounts for much of the
     * implementation complexity.
     * 当一个节点从队列中移除时，我们会摧毁这个节点对所有active节点的引用。
     * 我们开发出的自链接节点(self-links)在其他并发集合类里也是非常有效率的。
     *
     * Since neither unlinking nor gc-unlinking are necessary for
     * correctness, there are many implementation choices regarding
     * frequency (eagerness) of these operations.  Since volatile
     * reads are likely to be much cheaper than CASes, saving CASes by
     * unlinking multiple adjacent nodes at a time may be a win.
     * gc-unlinking can be performed rarely and still be effective,
     * since it is most important that long chains of deleted nodes
     * are occasionally broken.
     * 由于读volatile变量要比CAS操作更加廉价，所以在同一时间通过CAS解除多个相邻节点的链接也是一种优化技巧。
     * gc解除链接的执行可以很少，但它也是很有效的，因为最重要的是已删除节点组成的长链偶尔会被破坏。
     *
     *
     * The actual representation we use is that p.next == p means to
     * goto the first node (which in turn is reached by following prev
     * pointers from head), and p.next == null && p.prev == p means
     * that the iteration is at an end and that p is a (static final)
     * dummy node, NEXT_TERMINATOR, and not the last active node.
     * Finishing the iteration when encountering such a TERMINATOR is
     * good enough for read-only traversals, so such traversals can use
     * p.next == null as the termination condition.  When we need to
     * find the last (active) node, for enqueueing a new node, we need
     * to check whether we have reached a TERMINATOR node; if so,
     * restart traversal from tail.
     *
     * The implementation is completely directionally symmetrical,
     * except that most public methods that iterate through the list
     * follow next pointers ("forward" direction).
     *
     * We believe (without full proof) that all single-element deque
     * operations (e.g., addFirst, peekLast, pollLast) are linearizable
     * (see Herlihy and Shavit's book).  However, some combinations of
     * operations are known not to be linearizable.  In particular,
     * when an addFirst(A) is racing with pollFirst() removing B, it is
     * possible for an observer iterating over the elements to observe
     * A B C and subsequently observe A C, even though no interior
     * removes are ever performed.  Nevertheless, iterators behave
     * reasonably, providing the "weakly consistent" guarantees.
     *
     * Empirically, microbenchmarks suggest that this class adds about
     * 40% overhead relative to ConcurrentLinkedQueue, which feels as
     * good as we can hope for.
     * 相对于ConcurrentLinkedQueue，这个类多了40%的开销
     */

    private static final long serialVersionUID = 876323262645176354L;

    /**
     * A node from which the first node on list (that is, the unique node p
     * with p.prev == null && p.next != p) can be reached in O(1) time.
     * Invariants:
     * - the first node is always O(1) reachable from head via prev links
     * - all live nodes are reachable from the first node via succ()
     * - head != null
     * - (tmp = head).next != tmp || tmp != head
     * - head is never gc-unlinked (but may be unlinked)
     * Non-invariants:
     * - head.item may or may not be null
     * - head may not be reachable from the first or last node, or from tail
     */
    /**
     * 在执行方法之前和之后，head 必须保持的不变式：
     * 第一个节点总是能以O(1)的时间复杂度从head通过prev链接到达
     * 所有live节点（item不为null的节点），都能从第一个节点通过调用 succ() 方法遍历可达。
     * head 不能为 null。
     * head 节点的 next 域不能引用到自身。
     * head 不会是GC-unlinked节点（但它可能是unlink节点）
     * 在执行方法之前和之后，head 的可变式：
     * head 节点的 item 域可能为 null，也可能不为 null。
     * head 节点可能从first/last/tail 节点访问时不可达
     */
    private transient volatile Node<E> head;

    /**
     * A node from which the last node on list (that is, the unique node p
     * with p.next == null && p.prev != p) can be reached in O(1) time.
     * Invariants:
     * - the last node is always O(1) reachable from tail via next links
     * - all live nodes are reachable from the last node via pred()
     * - tail != null
     * - tail is never gc-unlinked (but may be unlinked)
     * Non-invariants:
     * - tail.item may or may not be null
     * - tail may not be reachable from the first or last node, or from head
     */
    /**
     * 在执行方法之前和之后，tail 必须保持的不变式：
     * 最后一个节点总是能以O(1)的时间复杂度从 tail 通过 next 链接到达
     * 所有live节点（item不为null的节点），都能从最后一个节点通过调用 pred() 方法遍历可达。
     * tail 不能为 null。
     * tail 不会是GC-unlinked节点（但它可能是unlink节点）
     * 在执行方法之前和之后，tail 的可变式：
     * tail 节点的 item 域可能为 null，也可能不为 null。
     * tail 节点可能从first/last/head 节点访问时不可达
     */
    private transient volatile Node<E> tail;

    /**
     * pre的终止节点(PREV_TERMINATOR.next = PREV_TERMINATOR)
     * next的终止节点(NEXT_TERMINATOR.pre = NEXT_TERMINATOR)
     * if(x.prev==first) x.prev=PREV_TERMINATOR
     * if(x.next==last) x.next=NEXT_TERMINATOR
     */
    private static final Node<Object> PREV_TERMINATOR, NEXT_TERMINATOR;

    @SuppressWarnings("unchecked")
    Node<E> prevTerminator() {
        return (Node<E>) PREV_TERMINATOR;
    }

    @SuppressWarnings("unchecked")
    Node<E> nextTerminator() {
        return (Node<E>) NEXT_TERMINATOR;
    }

    static final class Node<E> {
        volatile Node<E> prev;
        volatile E item;
        volatile Node<E> next;

        Node() {  // default constructor for NEXT_TERMINATOR, PREV_TERMINATOR
        }

        /**
         * Constructs a new node.  Uses relaxed write because item can
         * only be seen after publication via casNext or casPrev.
         */
        Node(E item) {
            UNSAFE.putObject(this, itemOffset, item);
        }

        boolean casItem(E cmp, E val) {
            return UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
        }

        void lazySetNext(Node<E> val) {
            UNSAFE.putOrderedObject(this, nextOffset, val);
        }

        boolean casNext(Node<E> cmp, Node<E> val) {
            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }

        void lazySetPrev(Node<E> val) {
            UNSAFE.putOrderedObject(this, prevOffset, val);
        }

        boolean casPrev(Node<E> cmp, Node<E> val) {
            return UNSAFE.compareAndSwapObject(this, prevOffset, cmp, val);
        }

        // Unsafe mechanics

        private static final sun.misc.Unsafe UNSAFE;
        private static final long prevOffset;
        private static final long itemOffset;
        private static final long nextOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = Node.class;
                prevOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("prev"));
                itemOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("item"));
                nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Links e as first element.
     */
    /**
     * 入列，插入到队列头
     */
    private void linkFirst(E e) {
        checkNotNull(e);
        final Node<E> newNode = new Node<E>(e);

        restartFromHead:
        for (; ; )
            //从head节点往前寻找first节点
            for (Node<E> h = head, p = h, q; ; ) {
                if ((q = p.prev) != null &&
                        (q = (p = q).prev) != null)
                    // Check for head updates every other hop.
                    // If p == q, we are sure to follow head instead.
                    //如果head被修改，返回head重新查找
                    p = (h != (h = head)) ? h : q;
                else if (p.next == p) // 自链接节点，重新查找
                    continue restartFromHead;
                else {
                    // p is first node
                    newNode.lazySetNext(p); // CAS piggyback
                    if (p.casPrev(null, newNode)) {
                        // Successful CAS is the linearization point
                        // for e to become an element of this deque,
                        // and for newNode to become "live".
                        if (p != h) // hop two nodes at a time 跳两个节点时才修改head
                            casHead(h, newNode);  // Failure is OK.
                        return;
                    }
                    // Lost CAS race to another thread; re-read prev
                }
            }
    }

    /**
     * Links e as last element.
     */
    /**
     * 入列，插入到队列尾
     */
    private void linkLast(E e) {
        checkNotNull(e);
        final Node<E> newNode = new Node<E>(e);

        restartFromTail:
        for (; ; )
            //从tail节点往后寻找last节点
            for (Node<E> t = tail, p = t, q; ; ) {
                if ((q = p.next) != null &&
                        (q = (p = q).next) != null)
                    // Check for tail updates every other hop.
                    // If p == q, we are sure to follow tail instead.
                    //如果tail被修改，返回tail重新查找
                    p = (t != (t = tail)) ? t : q;
                else if (p.prev == p) // 自链接节点，重新查找
                    continue restartFromTail;
                else {
                    // p is last node
                    newNode.lazySetPrev(p); // CAS piggyback
                    if (p.casNext(null, newNode)) {
                        // Successful CAS is the linearization point
                        // for e to become an element of this deque,
                        // and for newNode to become "live".
                        if (p != t) // hop two nodes at a time 跳两个节点时才修改tail
                            casTail(t, newNode);  // Failure is OK.
                        return;
                    }
                    // Lost CAS race to another thread; re-read next
                }
            }
    }

    /**
     * 更新 head/tail 的阀值
     */
    private static final int HOPS = 2;

    /**
     * Unlinks non-null node x.
     */
    /**
     * 移除给定节点
     */
    void unlink(Node<E> x) {
        // assert x != null;
        // assert x.item == null;
        // assert x != PREV_TERMINATOR;
        // assert x != NEXT_TERMINATOR;

        final Node<E> prev = x.prev;
        final Node<E> next = x.next;
        if (prev == null) {//操作节点为first节点
            unlinkFirst(x, next);
        } else if (next == null) {//操作节点为last节点
            unlinkLast(x, prev);
        } else {// common case
            // Unlink interior node.
            //
            // This is the common case, since a series of polls at the
            // same end will be "interior" removes, except perhaps for
            // the first one, since end nodes cannot be unlinked.
            //
            // At any time, all active nodes are mutually reachable by
            // following a sequence of either next or prev pointers.
            //
            // Our strategy is to find the unique active predecessor
            // and successor of x.  Try to fix up their links so that
            // they point to each other, leaving x unreachable from
            // active nodes.  If successful, and if x has no live
            // predecessor/successor, we additionally try to gc-unlink,
            // leaving active nodes unreachable from x, by rechecking
            // that the status of predecessor and successor are
            // unchanged and ensuring that x is not reachable from
            // tail/head, before setting x's prev/next links to their
            // logical approximate replacements, self/TERMINATOR.
            /*
             * 我们的策略是找到x节点的活跃（active）前继和后继节点。
             * 尝试修整它们之间的链接，让它们指向对方，留下一个从活跃(active)节点不可达的x节点。
             * 如果成功执行，或者x节点没有live的前继/后继节点，再尝试gc解除链接(gc-unlink)，
             * 在设置x节点的prev/next指向它们自己或TERMINATOR之前，
             * 需要检查x的前继和后继节点的状态未被改变，并保证x节点从head/tail不可达。
             */
            Node<E> activePred, activeSucc;
            boolean isFirst, isLast;
            int hops = 1;

            // Find active predecessor
            //从被操作节点的prev节点开始找到前继活动节点
            for (Node<E> p = prev; ; ++hops) {          //b
                if (p.item != null) {
                    activePred = p;
                    isFirst = false;
                    break;
                }
                Node<E> q = p.prev;
                if (q == null) {
                    if (p.next == p)
                        return;//自链接节点
                    activePred = p;
                    isFirst = true;
                    break;
                } else if (p == q)//自链接节点
                    return;
                else
                    p = q;
            }

            // Find active successor
            for (Node<E> p = next; ; ++hops) {          //c
                if (p.item != null) {
                    activeSucc = p;
                    isLast = false;
                    break;
                }
                Node<E> q = p.next;
                if (q == null) {
                    if (p.prev == p)
                        return;
                    activeSucc = p;
                    isLast = true;
                    break;
                } else if (p == q)//自链接节点
                    return;
                else
                    p = q;
            }

            // TODO: better HOP heuristics
            //无节点跳跃并且操作节点有first或last节点时不更新链表
            if (hops < HOPS
                    // always squeeze out interior deleted nodes
                    && (isFirst | isLast))          //d
                return;

            // Squeeze out deleted nodes between activePred and
            // activeSucc, including x.
            //连接两个活动节点
            skipDeletedSuccessors(activePred);            //e
            skipDeletedPredecessors(activeSucc);

            // Try to gc-unlink, if possible
            if ((isFirst | isLast) &&

                    // Recheck expected state of predecessor and successor
                    (activePred.next == activeSucc) &&
                    (activeSucc.prev == activePred) &&
                    (isFirst ? activePred.prev == null : activePred.item != null) &&
                    (isLast ? activeSucc.next == null : activeSucc.item != null)) {          //f

                updateHead(); // Ensure x is not reachable from head
                updateTail(); // Ensure x is not reachable from tail

                // Finally, actually gc-unlink
                x.lazySetPrev(isFirst ? prevTerminator() : x);
                x.lazySetNext(isLast ? nextTerminator() : x);
            }
        }
    }

    /**
     * Unlinks non-null first node.
     */
    /**
     * 出列，解除头结点链接
     */
    private void unlinkFirst(Node<E> first, Node<E> next) {
        // assert first != null;
        // assert next != null;
        // assert first.item == null;
        //从next节点开始向后寻找有效节点，o：已删除节点(item为null)
        for (Node<E> o = null, p = next, q; ; ) {
            if (p.item != null || (q = p.next) == null) {
                //跳过已删除节点，CAS替换first的next节点为一个active节点p
                if (o != null && p.prev != p && first.casNext(next, p)) {
                    //更新p的prev节点
                    skipDeletedPredecessors(p);
                    if (first.prev == null &&
                            (p.next == null || p.item != null) &&
                            p.prev == first) {
                        //更新head节点，确保已删除节点o从head不可达(unlinking)
                        updateHead(); // Ensure o is not reachable from head
                        //更新tail节点，确保已删除节点o从tail不可达(unlinking)
                        updateTail(); // Ensure o is not reachable from tail

                        // Finally, actually gc-unlink
                        //使unlinking节点next指向自身
                        o.lazySetNext(o);
                        //设置移除节点的prev为PREV_TERMINATOR
                        o.lazySetPrev(prevTerminator());
                    }
                }
                return;
            } else if (p == q)//自链接节点
                return;
            else {
                o = p;
                p = q;
            }
        }
    }

    /**
     * Unlinks non-null last node.
     */
    /**
     * 出列，移除队列尾节点
     */
    private void unlinkLast(Node<E> last, Node<E> prev) {
        // assert last != null;
        // assert prev != null;
        // assert last.item == null;
        for (Node<E> o = null, p = prev, q; ; ) {
            if (p.item != null || (q = p.prev) == null) {
                if (o != null && p.next != p && last.casPrev(prev, p)) {
                    skipDeletedSuccessors(p);
                    if (last.next == null &&
                            (p.prev == null || p.item != null) &&
                            p.next == last) {

                        updateHead(); // Ensure o is not reachable from head
                        updateTail(); // Ensure o is not reachable from tail

                        // Finally, actually gc-unlink
                        o.lazySetPrev(o);
                        o.lazySetNext(nextTerminator());
                    }
                }
                return;
            } else if (p == q)
                return;
            else {
                o = p;
                p = q;
            }
        }
    }

    /**
     * Guarantees that any node which was unlinked before a call to
     * this method will be unreachable from head after it returns.
     * Does not guarantee to eliminate slack, only that head will
     * point to a node that was active while this method was running.
     */
    /**
     * 更新头节点
     */
    private final void updateHead() {
        // Either head already points to an active node, or we keep
        // trying to cas it to the first node until it does.
        Node<E> h, p, q;
        restartFromHead:
        //从head开始往前查找
        while ((h = head).item == null && (p = h.prev) != null) {
            for (; ; ) {
                if ((q = p.prev) == null ||
                        (q = (p = q).prev) == null) {
                    // It is possible that p is PREV_TERMINATOR,
                    // but if so, the CAS is guaranteed to fail.
                    //p可能为prev的终止节点，替换head为p节点
                    if (casHead(h, p))
                        return;
                    else
                        continue restartFromHead;
                } else if (h != head)//head被其他线程修改
                    continue restartFromHead;
                else
                    p = q;//继续往下寻找
            }
        }
    }

    /**
     * Guarantees that any node which was unlinked before a call to
     * this method will be unreachable from tail after it returns.
     * Does not guarantee to eliminate slack, only that tail will
     * point to a node that was active while this method was running.
     */
    /**
     * 更新尾节点
     */
    private final void updateTail() {
        // Either tail already points to an active node, or we keep
        // trying to cas it to the last node until it does.
        Node<E> t, p, q;
        restartFromTail:
        while ((t = tail).item == null && (p = t.next) != null) {
            for (; ; ) {
                if ((q = p.next) == null ||
                        (q = (p = q).next) == null) {
                    // It is possible that p is NEXT_TERMINATOR,
                    // but if so, the CAS is guaranteed to fail.
                    if (casTail(t, p))
                        return;
                    else
                        continue restartFromTail;
                } else if (t != tail)
                    continue restartFromTail;
                else
                    p = q;
            }
        }
    }

    /**
     * 更新指定节点的前节点
     */
    private void skipDeletedPredecessors(Node<E> x) {
        whileActive:
        do {
            Node<E> prev = x.prev;
            // assert prev != null;
            // assert x != NEXT_TERMINATOR;
            // assert x != PREV_TERMINATOR;
            Node<E> p = prev;
            //找到有效活跃节点(item不为空或者前节点为first的节点)
            findActive:
            for (; ; ) {
                if (p.item != null)
                    break findActive;
                Node<E> q = p.prev;
                if (q == null) {
                    if (p.next == p)//p为self-node，重新查找
                        continue whileActive;
                    break findActive;//p为first节点
                } else if (p == q)//自链接节点，重新查找
                    continue whileActive;
                else
                    p = q;//继续往前寻找有效节点
            }

            // found active CAS target
            if (prev == p || x.casPrev(prev, p))//跳跃两次以上才更新前节点
                return;

        } while (x.item != null || x.next == null);
    }

    /**
     * 更新指定节点的后继节点
     */
    private void skipDeletedSuccessors(Node<E> x) {
        whileActive:
        do {
            Node<E> next = x.next;
            // assert next != null;
            // assert x != NEXT_TERMINATOR;
            // assert x != PREV_TERMINATOR;
            Node<E> p = next;
            //找到有效活跃节点(item不为空或者后节点为last的节点)
            findActive:
            for (; ; ) {
                if (p.item != null)
                    break findActive;
                Node<E> q = p.next;
                if (q == null) {
                    if (p.prev == p)//p可能为last节点
                        continue whileActive;
                    break findActive;
                } else if (p == q)
                    continue whileActive;
                else
                    p = q;//继续往后寻找
            }

            // found active CAS target
            if (next == p || x.casNext(next, p))//cas更新next节点
                return;

        } while (x.item != null || x.prev == null);
    }

    /**
     * Returns the successor of p, or the first node if p.next has been
     * linked to self, which will only be true if traversing with a
     * stale pointer that is now off the list.
     */
    /**
     * 返回指定节点的的后继节点，如果指定节点的next指向自己，返回first节点
     */
    final Node<E> succ(Node<E> p) {
        // TODO: should we skip deleted nodes here?
        Node<E> q = p.next;
        return (p == q) ? first() : q;
    }

    /**
     * Returns the predecessor of p, or the last node if p.prev has been
     * linked to self, which will only be true if traversing with a
     * stale pointer that is now off the list.
     */
    /**
     * 返回指定节点的的前继节点，如果指定节点的prev指向自己，返回last节点
     */
    final Node<E> pred(Node<E> p) {
        Node<E> q = p.prev;
        return (p == q) ? last() : q;
    }

    /**
     * Returns the first node, the unique node p for which:
     *     p.prev == null && p.next != p
     * The returned node may or may not be logically deleted.
     * Guarantees that head is set to the returned node.
     */
    /**
     * 返回首节点
     */
    Node<E> first() {
        restartFromHead:
        for (; ; )
            //从head开始往前找
            for (Node<E> h = head, p = h, q; ; ) {
                if ((q = p.prev) != null &&
                        (q = (p = q).prev) != null)
                    // Check for head updates every other hop.
                    // If p == q, we are sure to follow head instead.
                    //如果head被修改则返回新的head重新查找，否则继续向前(prev)查找
                    p = (h != (h = head)) ? h : q;
                else if (p == h
                        // It is possible that p is PREV_TERMINATOR,
                        // but if so, the CAS is guaranteed to fail.
                        //找到的节点不是head节点，CAS修改head
                        || casHead(h, p))
                    return p;
                else
                    continue restartFromHead;
            }
    }

    /**
     * Returns the last node, the unique node p for which:
     *     p.next == null && p.prev != p
     * The returned node may or may not be logically deleted.
     * Guarantees that tail is set to the returned node.
     */
    /**
     * 返回尾节点
     */
    Node<E> last() {
        restartFromTail:
        for (; ; )
            for (Node<E> t = tail, p = t, q; ; ) {
                if ((q = p.next) != null &&
                        (q = (p = q).next) != null)
                    // Check for tail updates every other hop.
                    // If p == q, we are sure to follow tail instead.
                    p = (t != (t = tail)) ? t : q;
                else if (p == t
                        // It is possible that p is NEXT_TERMINATOR,
                        // but if so, the CAS is guaranteed to fail.
                        || casTail(t, p))
                    return p;
                else
                    continue restartFromTail;
            }
    }

    // Minor convenience utilities

    /**
     * Throws NullPointerException if argument is null.
     *
     * @param v the element
     */
    private static void checkNotNull(Object v) {
        if (v == null)
            throw new NullPointerException();
    }

    /**
     * Returns element unless it is null, in which case throws
     * NoSuchElementException.
     *
     * @param v the element
     * @return the element
     */
    private E screenNullResult(E v) {
        if (v == null)
            throw new NoSuchElementException();
        return v;
    }

    /**
     * Creates an array list and fills it with elements of this list.
     * Used by toArray.
     *
     * @return the array list
     */
    private ArrayList<E> toArrayList() {
        ArrayList<E> list = new ArrayList<E>();
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null)
                list.add(item);
        }
        return list;
    }

    /**
     * Constructs an empty deque.
     */
    public ConcurrentLinkedDeque() {
        head = tail = new Node<E>(null);
    }

    /**
     * Constructs a deque initially containing the elements of
     * the given collection, added in traversal order of the
     * collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *                              of its elements are null
     */
    public ConcurrentLinkedDeque(Collection<? extends E> c) {
        // Copy c into a private chain of Nodes
        Node<E> h = null, t = null;
        for (E e : c) {
            checkNotNull(e);
            Node<E> newNode = new Node<E>(e);
            if (h == null)
                h = t = newNode;
            else {
                t.lazySetNext(newNode);
                newNode.lazySetPrev(t);
                t = newNode;
            }
        }
        initHeadTail(h, t);
    }

    /**
     * Initializes head and tail, ensuring invariants hold.
     */
    private void initHeadTail(Node<E> h, Node<E> t) {
        if (h == t) {
            if (h == null)
                h = t = new Node<E>(null);
            else {
                // Avoid edge case of a single Node with non-null item.
                Node<E> newNode = new Node<E>(null);
                t.lazySetNext(newNode);
                newNode.lazySetPrev(t);
                t = newNode;
            }
        }
        head = h;
        tail = t;
    }

    /**
     * Inserts the specified element at the front of this deque.
     * As the deque is unbounded, this method will never throw
     * {@link IllegalStateException}.
     *
     * @throws NullPointerException if the specified element is null
     */
    /**
     * 添加元素到队列头
     */
    public void addFirst(E e) {
        linkFirst(e);
    }

    /**
     * Inserts the specified element at the end of this deque.
     * As the deque is unbounded, this method will never throw
     * {@link IllegalStateException}.
     *
     * <p>This method is equivalent to {@link #add}.
     *
     * @throws NullPointerException if the specified element is null
     */
    /**
     * 添加元素到队列尾
     */
    public void addLast(E e) {
        linkLast(e);
    }

    /**
     * Inserts the specified element at the front of this deque.
     * As the deque is unbounded, this method will never return {@code false}.
     *
     * @return {@code true} (as specified by {@link Deque#offerFirst})
     * @throws NullPointerException if the specified element is null
     */
    /**
     * 添加元素到队列头，成功返回true
     */
    public boolean offerFirst(E e) {
        linkFirst(e);
        return true;
    }

    /**
     * Inserts the specified element at the end of this deque.
     * As the deque is unbounded, this method will never return {@code false}.
     *
     * <p>This method is equivalent to {@link #add}.
     *
     * @return {@code true} (as specified by {@link Deque#offerLast})
     * @throws NullPointerException if the specified element is null
     */
    /**
     * 添加元素到队列尾，成功返回true
     */
    public boolean offerLast(E e) {
        linkLast(e);
        return true;
    }

    /**
     * 获取队列首节点
     */
    public E peekFirst() {
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null)
                return item;
        }
        return null;
    }

    /**
     * 获取队列尾节点
     */
    public E peekLast() {
        for (Node<E> p = last(); p != null; p = pred(p)) {
            E item = p.item;
            if (item != null)
                return item;
        }
        return null;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    /**
     * 获取队列首节点，失败抛出NoSuchElementException
     */
    public E getFirst() {
        return screenNullResult(peekFirst());
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    /**
     * 获取队列尾节点，失败抛出NoSuchElementException
     */
    public E getLast() {
        return screenNullResult(peekLast());
    }

    /**
     * 获取并移除队列首节点
     */
    public E pollFirst() {
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null && p.casItem(item, null)) {//cas进行逻辑删除(item设为null)
                unlink(p);
                return item;
            }
        }
        return null;
    }

    /**
     * 获取并移除队列尾节点
     */
    public E pollLast() {
        for (Node<E> p = last(); p != null; p = pred(p)) {
            E item = p.item;
            if (item != null && p.casItem(item, null)) {
                unlink(p);
                return item;
            }
        }
        return null;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    /**
     * 获取并移除队列首节点，NoSuchElementException
     */
    public E removeFirst() {
        return screenNullResult(pollFirst());
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    /**
     * 获取并移除队列尾节点，NoSuchElementException
     */
    public E removeLast() {
        return screenNullResult(pollLast());
    }

    // *** Queue and stack methods ***

    /**
     * Inserts the specified element at the tail of this deque.
     * As the deque is unbounded, this method will never return {@code false}.
     *
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws NullPointerException if the specified element is null
     */
    /**
     * 添加节点到队列尾{@link Queue#offer}
     */
    public boolean offer(E e) {
        return offerLast(e);
    }

    /**
     * Inserts the specified element at the tail of this deque.
     * As the deque is unbounded, this method will never throw
     * {@link IllegalStateException} or return {@code false}.
     *
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    /**
     * 添加节点到队列尾{@link Collection#add}
     */
    public boolean add(E e) {
        return offerLast(e);
    }

    /**
     * 获取并移除队列头节点
     */
    public E poll() {
        return pollFirst();
    }

    /**
     * 获取队列头节点，不移除
     */
    public E peek() {
        return peekFirst();
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    /**
     * 获取并移除队列头节点，失败抛出NoSuchElementException
     */
    public E remove() {
        return removeFirst();
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    /**
     * 获取并移除队列头节点，失败抛出NoSuchElementException
     */
    public E pop() {
        return removeFirst();
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    /**
     * 获取队列头节点，不移除
     */
    public E element() {
        return getFirst();
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    /**
     * 添加节点到队列头
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     * Removes the first element {@code e} such that
     * {@code o.equals(e)}, if such an element exists in this deque.
     * If the deque does not contain the element, it is unchanged.
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if the deque contained the specified element
     * @throws NullPointerException if the specified element is null
     */
    /**
     * 从队列头开始向后寻找，移除指定元素所在的节点
     */
    public boolean removeFirstOccurrence(Object o) {
        checkNotNull(o);
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null && o.equals(item) && p.casItem(item, null)) {
                unlink(p);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the last element {@code e} such that
     * {@code o.equals(e)}, if such an element exists in this deque.
     * If the deque does not contain the element, it is unchanged.
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if the deque contained the specified element
     * @throws NullPointerException if the specified element is null
     */
    /**
     * 从队列尾开始向前寻找，移除指定元素所在的节点
     */
    public boolean removeLastOccurrence(Object o) {
        checkNotNull(o);
        for (Node<E> p = last(); p != null; p = pred(p)) {
            E item = p.item;
            if (item != null && o.equals(item) && p.casItem(item, null)) {
                unlink(p);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if this deque contains at least one
     * element {@code e} such that {@code o.equals(e)}.
     *
     * @param o element whose presence in this deque is to be tested
     * @return {@code true} if this deque contains the specified element
     */
    /**
     * 返回是否包含指定元素
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null && o.equals(item))
                return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if this collection contains no elements.
     *
     * @return {@code true} if this collection contains no elements
     */
    /**
     * 返回是否为空队列
     */
    public boolean isEmpty() {
        return peekFirst() == null;
    }

    /**
     * Returns the number of elements in this deque.  If this deque
     * contains more than {@code Integer.MAX_VALUE} elements, it
     * returns {@code Integer.MAX_VALUE}.
     *
     * <p>Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these deques, determining the current
     * number of elements requires traversing them all to count them.
     * Additionally, it is possible for the size to change during
     * execution of this method, in which case the returned result
     * will be inaccurate. Thus, this method is typically not very
     * useful in concurrent applications.
     *
     * @return the number of elements in this deque
     */
    /**
     * 返回队列元素数量，不一定准确
     */
    public int size() {
        int count = 0;
        for (Node<E> p = first(); p != null; p = succ(p))
            if (p.item != null)
                // Collection.size() spec says to max out
                if (++count == Integer.MAX_VALUE)
                    break;
        return count;
    }

    /**
     * Removes the first element {@code e} such that
     * {@code o.equals(e)}, if such an element exists in this deque.
     * If the deque does not contain the element, it is unchanged.
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if the deque contained the specified element
     * @throws NullPointerException if the specified element is null
     */
    /**
     * 从队列头开始寻找，移除指定元素
     */
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this deque, in the order that they are returned by the specified
     * collection's iterator.  Attempts to {@code addAll} of a deque to
     * itself result in {@code IllegalArgumentException}.
     *
     * @param c the elements to be inserted into this deque
     * @return {@code true} if this deque changed as a result of the call
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     * @throws IllegalArgumentException if the collection is this deque
     */
    /**
     * 添加指定Collection到队列中
     */
    public boolean addAll(Collection<? extends E> c) {
        if (c == this)
            // As historically specified in AbstractQueue#addAll
            throw new IllegalArgumentException();

        // Copy c into a private chain of Nodes
        Node<E> beginningOfTheEnd = null, last = null;
        for (E e : c) {
            checkNotNull(e);
            Node<E> newNode = new Node<E>(e);
            if (beginningOfTheEnd == null)
                beginningOfTheEnd = last = newNode;
            else {
                last.lazySetNext(newNode);
                newNode.lazySetPrev(last);
                last = newNode;
            }
        }
        if (beginningOfTheEnd == null)
            return false;

        // Atomically append the chain at the tail of this collection
        restartFromTail:
        for (; ; )
            for (Node<E> t = tail, p = t, q; ; ) {
                if ((q = p.next) != null &&
                        (q = (p = q).next) != null)
                    // Check for tail updates every other hop.
                    // If p == q, we are sure to follow tail instead.
                    p = (t != (t = tail)) ? t : q;
                else if (p.prev == p) // NEXT_TERMINATOR
                    continue restartFromTail;
                else {
                    // p is last node
                    beginningOfTheEnd.lazySetPrev(p); // CAS piggyback
                    if (p.casNext(null, beginningOfTheEnd)) {
                        // Successful CAS is the linearization point
                        // for all elements to be added to this deque.
                        if (!casTail(t, last)) {
                            // Try a little harder to update tail,
                            // since we may be adding many elements.
                            t = tail;
                            if (last.next == null)
                                casTail(t, last);
                        }
                        return true;
                    }
                    // Lost CAS race to another thread; re-read next
                }
            }
    }

    /**
     * Removes all of the elements from this deque.
     */
    /**
     * 清空队列
     */
    public void clear() {
        while (pollFirst() != null)
            ;
    }

    /**
     * Returns an array containing all of the elements in this deque, in
     * proper sequence (from first to last element).
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this deque.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this deque
     */
    /**
     * 返回队列的所有元素数组
     */
    public Object[] toArray() {
        return toArrayList().toArray();
    }

    /**
     * Returns an array containing all of the elements in this deque,
     * in proper sequence (from first to last element); the runtime
     * type of the returned array is that of the specified array.  If
     * the deque fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of
     * the specified array and the size of this deque.
     *
     * <p>If this deque fits in the specified array with room to spare
     * (i.e., the array has more elements than this deque), the element in
     * the array immediately following the end of the deque is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as
     * bridge between array-based and collection-based APIs.  Further,
     * this method allows precise control over the runtime type of the
     * output array, and may, under certain circumstances, be used to
     * save allocation costs.
     *
     * <p>Suppose {@code x} is a deque known to contain only strings.
     * The following code can be used to dump the deque into a newly
     * allocated array of {@code String}:
     *
     *  <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the deque are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this deque
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this deque
     * @throws NullPointerException if the specified array is null
     */
    /**
     * 返回队列的所有元素数组，指定元素类型
     */
    public <T> T[] toArray(T[] a) {
        return toArrayList().toArray(a);
    }

    /**
     * Returns an iterator over the elements in this deque in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this deque in proper sequence
     */
    /**
     * 正序迭代器
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * Returns an iterator over the elements in this deque in reverse
     * sequential order.  The elements will be returned in order from
     * last (tail) to first (head).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this deque in reverse order
     */
    /**
     * 反序迭代器
     */
    public Iterator<E> descendingIterator() {
        return new DescendingItr();
    }

    private abstract class AbstractItr implements Iterator<E> {
        /**
         * Next node to return item for.
         */
        private Node<E> nextNode;

        /**
         * nextItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         */
        private E nextItem;

        /**
         * Node returned by most recent call to next. Needed by remove.
         * Reset to null if this element is deleted by a call to remove.
         */
        private Node<E> lastRet;

        abstract Node<E> startNode();

        abstract Node<E> nextNode(Node<E> p);

        AbstractItr() {
            advance();
        }

        /**
         * Sets nextNode and nextItem to next valid node, or to null
         * if no such.
         */
        private void advance() {
            lastRet = nextNode;

            Node<E> p = (nextNode == null) ? startNode() : nextNode(nextNode);
            for (; ; p = nextNode(p)) {
                if (p == null) {
                    // p might be active end or TERMINATOR node; both are OK
                    nextNode = null;
                    nextItem = null;
                    break;
                }
                E item = p.item;
                if (item != null) {
                    nextNode = p;
                    nextItem = item;
                    break;
                }
            }
        }

        public boolean hasNext() {
            return nextItem != null;
        }

        public E next() {
            E item = nextItem;
            if (item == null) throw new NoSuchElementException();
            advance();
            return item;
        }

        public void remove() {
            Node<E> l = lastRet;
            if (l == null) throw new IllegalStateException();
            l.item = null;
            unlink(l);
            lastRet = null;
        }
    }

    /**
     * Forward iterator
     */
    private class Itr extends AbstractItr {
        Node<E> startNode() {
            return first();
        }

        Node<E> nextNode(Node<E> p) {
            return succ(p);
        }
    }

    /**
     * Descending iterator
     */
    private class DescendingItr extends AbstractItr {
        Node<E> startNode() {
            return last();
        }

        Node<E> nextNode(Node<E> p) {
            return pred(p);
        }
    }

    /**
     * A customized variant of Spliterators.IteratorSpliterator
     */
    static final class CLDSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final ConcurrentLinkedDeque<E> queue;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes

        CLDSpliterator(ConcurrentLinkedDeque<E> queue) {
            this.queue = queue;
        }

        public Spliterator<E> trySplit() {
            Node<E> p;
            final ConcurrentLinkedDeque<E> q = this.queue;
            int b = batch;
            int n = (b <= 0) ? 1 : (b >= MAX_BATCH) ? MAX_BATCH : b + 1;
            if (!exhausted &&
                    ((p = current) != null || (p = q.first()) != null)) {
                if (p.item == null && p == (p = p.next))
                    current = p = q.first();
                if (p != null && p.next != null) {
                    Object[] a = new Object[n];
                    int i = 0;
                    do {
                        if ((a[i] = p.item) != null)
                            ++i;
                        if (p == (p = p.next))
                            p = q.first();
                    } while (p != null && i < n);
                    if ((current = p) == null)
                        exhausted = true;
                    if (i > 0) {
                        batch = i;
                        return Spliterators.spliterator
                                (a, 0, i, Spliterator.ORDERED | Spliterator.NONNULL |
                                        Spliterator.CONCURRENT);
                    }
                }
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            final ConcurrentLinkedDeque<E> q = this.queue;
            if (!exhausted &&
                    ((p = current) != null || (p = q.first()) != null)) {
                exhausted = true;
                do {
                    E e = p.item;
                    if (p == (p = p.next))
                        p = q.first();
                    if (e != null)
                        action.accept(e);
                } while (p != null);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            final ConcurrentLinkedDeque<E> q = this.queue;
            if (!exhausted &&
                    ((p = current) != null || (p = q.first()) != null)) {
                E e;
                do {
                    e = p.item;
                    if (p == (p = p.next))
                        p = q.first();
                } while (e == null && p != null);
                if ((current = p) == null)
                    exhausted = true;
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.NONNULL |
                    Spliterator.CONCURRENT;
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this deque.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @return a {@code Spliterator} over the elements in this deque
     * @implNote The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new CLDSpliterator<E>(this);
    }

    /**
     * Saves this deque to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData All of the elements (each an {@code E}) in
     * the proper order, followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {

        // Write out any hidden stuff
        s.defaultWriteObject();

        // Write out all elements in the proper order.
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null)
                s.writeObject(item);
        }

        // Use trailing null as sentinel
        s.writeObject(null);
    }

    /**
     * Reconstitutes this deque from a stream (that is, deserializes it).
     *
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *                                could not be found
     * @throws java.io.IOException    if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Read in elements until trailing null sentinel found
        Node<E> h = null, t = null;
        Object item;
        while ((item = s.readObject()) != null) {
            @SuppressWarnings("unchecked")
            Node<E> newNode = new Node<E>((E) item);
            if (h == null)
                h = t = newNode;
            else {
                t.lazySetNext(newNode);
                newNode.lazySetPrev(t);
                t = newNode;
            }
        }
        initHeadTail(h, t);
    }

    private boolean casHead(Node<E> cmp, Node<E> val) {
        return UNSAFE.compareAndSwapObject(this, headOffset, cmp, val);
    }

    private boolean casTail(Node<E> cmp, Node<E> val) {
        return UNSAFE.compareAndSwapObject(this, tailOffset, cmp, val);
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE;
    private static final long headOffset;
    private static final long tailOffset;

    static {
        PREV_TERMINATOR = new Node<Object>();
        PREV_TERMINATOR.next = PREV_TERMINATOR;
        NEXT_TERMINATOR = new Node<Object>();
        NEXT_TERMINATOR.prev = NEXT_TERMINATOR;
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = ConcurrentLinkedDeque.class;
            headOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("head"));
            tailOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("tail"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
