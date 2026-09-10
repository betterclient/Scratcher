import sensing::ask;
import cast;
import looks::say;
import utils::random;

on GreenFlag {
    LinkedList<int> myList = newLinkedList(5);

    repeat(15) {
        myList.add(random(1, 150));
    }

    Node<int>? current = myList.first;
    int currentBiggest = myList.first.value; // Also safer if values could be negative!

    while(current != null) {
        if(current!!.value > currentBiggest) {
            currentBiggest = current!!.value;
        }
        current = current!!.next;
    }

    say("The biggest value in our linked list is ${currentBiggest}");
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