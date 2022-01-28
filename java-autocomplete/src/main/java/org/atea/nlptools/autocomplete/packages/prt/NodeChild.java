package packages.prt;

import java.io.Serializable;

public class NodeChild implements Serializable {
    private TextSanitized key;
    private Node node;

    public TextSanitized getKeySanitized() {
        return key;
    }

    public void setKey(TextSanitized key) {
        this.key = key;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    @Override
    public String toString() {
        return "NodeChild [key=" + key + ", node=" + node + "]";
    }

    public NodeChild(TextSanitized key, Node node) {
        super();
        this.key = key;
        this.node = node;
    }
}
