package io.github.flyingpig525.serialization

import kotlinx.serialization.KSerializer

public fun <T> KSerializer<T>.generateHashCode(): Int {
    var hash = descriptor.serialName.hashCode()
    for (i in 0 until descriptor.elementsCount) {
        hash = 31 * hash + descriptor.getElementName(i).hashCode()
    }
    return hash
}