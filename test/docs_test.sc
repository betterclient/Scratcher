import sensing::ask;
import cast;
import looks::say;
import utils::random;
import list::*;
import array::*;

on GreenFlag {
    List<int> myList = [6, 7, 6, 6, 7, 6, 7, 6, 7, 6, 7].toList();

    List<int> newList = myList
        .map(
            (int item) -> item * 2
        )
        .filter(
            (int item) -> item > 2
        )
        .take(5)
        .flatMap(
            (int item) -> arrayOf(5, (int index) -> index * 5 + item).toList()
        );
}

struct Node<T>(
    T value,
    Node<T>? next
);

struct LinkedList<T>(
    Node<T> first
);

warp <T> LinkedList<T> newLinkedList(T object) {
    return LinkedList(
        Node(object, null)
    );
}

private warp <T> Node<T> LinkedList<T>.last() {
    Node<T> current = this.first;

    while(current.next != null) {
        current = current.next!!;
    }

    return current;
}

warp <T> void LinkedList<T>.add(T object) {
    this.last().next = Node(object, null);
}

warp <T> int LinkedList<T>.length() {
    Node<T>? current = this.first;
    int length = 0;

    while(current != null) {
        current = current!!.next;
        length++;
    }

    return length;
}

warp <T> void LinkedList<T>.forEach((T) -> void action) {
    Node<T>? current = this.first;

    while(current != null) {
        action(current!!.value);
        current = current!!.next;
    }
}

export int multiply(int num) {
    return num * 3;
}