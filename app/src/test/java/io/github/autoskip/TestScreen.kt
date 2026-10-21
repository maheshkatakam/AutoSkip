package io.github.autoskip

/** Builds a pre-order node list for tests. Add a node's children before its next sibling. */
class TestScreen {
    val nodes = ArrayList<UiNode>()

    fun add(
        parent: Int,
        id: String? = null,
        text: String = "",
        desc: String = "",
        clickable: Boolean = false,
        enabled: Boolean = true,
        visible: Boolean = true,
        scrollable: Boolean = false,
        box: Box = Box(0, 0, 100, 50),
    ): Int {
        val n = UiNode(
            nodes.size, parent, id, SkipDetector.normalize(text), SkipDetector.normalize(desc),
            clickable, enabled, visible, box, scrollable,
        )
        nodes += n
        var p = parent
        while (p >= 0) {
            nodes[p].subtreeEnd = n.index
            p = nodes[p].parent
        }
        return n.index
    }
}
