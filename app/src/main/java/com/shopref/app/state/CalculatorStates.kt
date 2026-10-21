package com.shopref.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shopref.core.calc.DrillingInput
import com.shopref.core.calc.MillingInput
import com.shopref.core.calc.TappingInput
import com.shopref.core.calc.TurningInput
import com.shopref.core.material.Material
import com.shopref.core.material.MaterialLibrary
import com.shopref.core.reference.Threads

/** Workpiece material shared by all calculators (persisted). */
class Session(private val store: KeyValueStore) {
    var material: Material by mutableStateOf(
        store.getString("material")?.let { MaterialLibrary.byId(it) } ?: MaterialLibrary.default,
    )
        private set

    fun selectMaterial(m: Material) {
        material = m
        store.putString("material", m.id)
    }
}

class MillingState {
    var input by mutableStateOf(MillingInput(material = MaterialLibrary.default))
        private set

    fun update(transform: (MillingInput) -> MillingInput) { input = transform(input) }
    fun reset() { input = MillingInput(material = MaterialLibrary.default) }
}

class DrillingState {
    var input by mutableStateOf(DrillingInput(material = MaterialLibrary.default))
        private set

    fun update(transform: (DrillingInput) -> DrillingInput) { input = transform(input) }
    fun reset() { input = DrillingInput(material = MaterialLibrary.default) }
}

class TappingState {
    var input by mutableStateOf(
        TappingInput(material = MaterialLibrary.default, thread = Threads.metricCoarse.first { it.designation == "M8×1.25" }),
    )
        private set

    fun update(transform: (TappingInput) -> TappingInput) { input = transform(input) }
    fun reset() { input = TappingInput(material = MaterialLibrary.default, thread = input.thread) }
}

class TurningState {
    var input by mutableStateOf(TurningInput(material = MaterialLibrary.default))
        private set

    fun update(transform: (TurningInput) -> TurningInput) { input = transform(input) }
    fun reset() { input = TurningInput(material = MaterialLibrary.default) }
}
