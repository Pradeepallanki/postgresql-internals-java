package com.archives;

/**
 * A textbook B+ tree mapping long keys to long values (here: row offsets).
 *
 * Shape:
 *   - Internal nodes hold separator keys and pointers to children.
 *   - Leaf nodes hold the real (key, value) pairs and are linked left-to-right
 *     (handy for range scans, not used here).
 *   - All real data lives in the leaves. Every leaf is at the same depth.
 *
 * Why this beats a full scan: tree height grows as log_ORDER(N).
 * With ORDER=64 and N=1,000,000 the tree is only ~4 levels deep, so a lookup
 * does ~4 small in-memory hops instead of touching every row.
 */
public class BPlusTree {

    private static final int ORDER = 64;

    sealed interface Node permits LeafNode, InternalNode {}

    static final class LeafNode implements Node {
        final long[] keys = new long[ORDER + 1];
        final long[] values = new long[ORDER + 1];
        int size;
        LeafNode next;
    }

    static final class InternalNode implements Node {
        final long[] keys = new long[ORDER + 1];
        final Node[] children = new Node[ORDER + 2];
        int size;
    }

    private Node root = new LeafNode();

    public Long find(long key) {
        Node node = root;
        while (node instanceof InternalNode in) {
            int i = 0;
            while (i < in.size && key >= in.keys[i]) i++;
            node = in.children[i];
        }
        LeafNode leaf = (LeafNode) node;
        for (int i = 0; i < leaf.size; i++) {
            if (leaf.keys[i] == key) return leaf.values[i];
        }
        return null;
    }

    public void insert(long key, long value) {
        Split split = insertInto(root, key, value);
        if (split != null) {
            InternalNode newRoot = new InternalNode();
            newRoot.keys[0] = split.key;
            newRoot.children[0] = root;
            newRoot.children[1] = split.right;
            newRoot.size = 1;
            root = newRoot;
        }
    }

    private record Split(long key, Node right) {}

    private Split insertInto(Node node, long key, long value) {
        if (node instanceof LeafNode leaf) {
            int i = 0;
            while (i < leaf.size && leaf.keys[i] < key) i++;
            if (i < leaf.size && leaf.keys[i] == key) {
                leaf.values[i] = value;
                return null;
            }
            for (int j = leaf.size; j > i; j--) {
                leaf.keys[j] = leaf.keys[j - 1];
                leaf.values[j] = leaf.values[j - 1];
            }
            leaf.keys[i] = key;
            leaf.values[i] = value;
            leaf.size++;
            return leaf.size > ORDER ? splitLeaf(leaf) : null;
        }

        InternalNode in = (InternalNode) node;
        int i = 0;
        while (i < in.size && key >= in.keys[i]) i++;
        Split child = insertInto(in.children[i], key, value);
        if (child == null) return null;

        for (int j = in.size; j > i; j--) {
            in.keys[j] = in.keys[j - 1];
            in.children[j + 1] = in.children[j];
        }
        in.keys[i] = child.key;
        in.children[i + 1] = child.right;
        in.size++;
        return in.size > ORDER ? splitInternal(in) : null;
    }

    private Split splitLeaf(LeafNode leaf) {
        int mid = leaf.size / 2;
        LeafNode right = new LeafNode();
        right.size = leaf.size - mid;
        System.arraycopy(leaf.keys, mid, right.keys, 0, right.size);
        System.arraycopy(leaf.values, mid, right.values, 0, right.size);
        leaf.size = mid;
        right.next = leaf.next;
        leaf.next = right;
        return new Split(right.keys[0], right);
    }

    private Split splitInternal(InternalNode in) {
        int mid = in.size / 2;
        long upKey = in.keys[mid];
        InternalNode right = new InternalNode();
        right.size = in.size - mid - 1;
        System.arraycopy(in.keys, mid + 1, right.keys, 0, right.size);
        System.arraycopy(in.children, mid + 1, right.children, 0, right.size + 1);
        in.size = mid;
        return new Split(upKey, right);
    }
}