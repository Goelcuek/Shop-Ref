package com.shopref.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shopref.core.tooling.ChipLoadLevel
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.Rigidity
import com.shopref.core.units.UnitSystem

/** Minimal key-value persistence so the state layer stays platform independent. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class InMemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String) = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
}

/** App-wide settings: unit system and the milling / turning machine profiles. */
class AppSettings(private val store: KeyValueStore) {
    var units by mutableStateOf(
        store.getString("units")?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.METRIC,
    )
        private set

    var mill by mutableStateOf(loadMachine("mill") ?: Machine.PRESETS.first())
        private set

    var lathe by mutableStateOf(loadMachine("lathe") ?: Machine.PRESETS.last())
        private set

    var chipLoadLevel by mutableStateOf(
        store.getString("chipLoadLevel")?.let { runCatching { ChipLoadLevel.valueOf(it) }.getOrNull() } ?: ChipLoadLevel.STANDARD,
    )
        private set

    fun updateChipLoadLevel(value: ChipLoadLevel) {
        chipLoadLevel = value
        store.putString("chipLoadLevel", value.name)
    }

    fun updateUnits(value: UnitSystem) {
        units = value
        store.putString("units", value.name)
    }

    fun updateMill(machine: Machine) {
        mill = machine
        saveMachine("mill", machine)
    }

    fun updateLathe(machine: Machine) {
        lathe = machine
        saveMachine("lathe", machine)
    }

    private fun saveMachine(prefix: String, m: Machine) {
        val encoded = listOf(
            m.name.replace("|", "/"), m.maxRpm, m.minRpm, m.maxFeed, m.powerKw, m.maxTorqueNm, m.efficiency, m.rigidity.name,
        ).joinToString("|")
        store.putString("machine_$prefix", encoded)
    }

    private fun loadMachine(prefix: String): Machine? {
        val parts = store.getString("machine_$prefix")?.split("|") ?: return null
        if (parts.size != 8) return null
        return runCatching {
            Machine(
                name = parts[0],
                maxRpm = parts[1].toDouble(),
                minRpm = parts[2].toDouble(),
                maxFeed = parts[3].toDouble(),
                powerKw = parts[4].toDouble(),
                maxTorqueNm = parts[5].toDouble(),
                efficiency = parts[6].toDouble(),
                rigidity = Rigidity.valueOf(parts[7]),
            )
        }.getOrNull()
    }
}
