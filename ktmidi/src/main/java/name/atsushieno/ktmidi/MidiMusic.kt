@file:Suppress("unused")

package name.atsushieno.ktmidi

import java.io.InputStream
import java.io.OutputStream
import java.lang.IndexOutOfBoundsException

internal fun Byte.toUnsigned() = if (this < 0) 256 + this else this.toInt()

internal fun OutputStream.writeByte(b : Byte)
{
    val arr = byteArrayOf(b)
    this.write(arr, 0, 1)
}

class Midi2Music {
    companion object {
        fun read (stream : InputStream) : Midi2Music {
            val r = SmfReader (stream)
            r.read ()
            return r.music
        }

        // We treat SYSEX8 of Univ. SysEx (Manufacturer 0x7E) with NACK (SubID 0x7E) as META events.
        // The resulting UMPs contain only JR timestamp and SYSEX8 of such ones.
        fun getMetaEventsOfType (messages : Iterable<Midi2Event>, metaType : Byte) = sequence {
            var inMeta = false
            for (m in messages) {
                if (m.messageType == MidiMessageType.UTILITY && m.eventType == MidiEventType.JR_TIMESTAMP)
                    yield(m)
                // If it is beginning of META messages, switch to inMeta mode, and send JR timestamp for this event (delta since last meta).
                if (m.messageType == MidiMessageType.SYSEX8_MDS && (m.eventType == 0x0.toByte() || m.eventType == 0x1.toByte()) &&
                    (m.second64Bits and 0xFF000000u) == 0x7E000000UL &&
                    (m.second64Bits and 0xFF0000u) == 0x7E0000UL) {
                    inMeta = true
                }
                if (inMeta) {
                    if (m.messageType == MidiMessageType.SYSEX8_MDS && (m.eventType != 0x0.toByte() || m.eventType == 0x3.toByte()))
                        inMeta = false
                    else
                        yield(m)
                }
            }
        }

        fun getTotalPlayTimeMilliseconds (messages : MutableList<MidiMessage>, deltaTimeSpec : Int) : Int
        {
            return getPlayTimeMillisecondsAtTick (messages, messages.sumBy { m -> m.deltaTime}, deltaTimeSpec)
        }

        fun getPlayTimeMillisecondsAtTick (messages : List<MidiMessage>, ticks : Int, deltaTimeSpec : Int) : Int
        {
            if (deltaTimeSpec < 0)
                throw UnsupportedOperationException ("non-tick based DeltaTime")
            else {
                var tempo : Int = MidiMetaType.DEFAULT_TEMPO
                var v = 0
                var t = 0
                for (m in messages) {
                    val deltaTime = t + if (m.deltaTime < ticks) m.deltaTime else ticks - t
                    v += (tempo / 1000 * deltaTime / deltaTimeSpec)
                    if (deltaTime != m.deltaTime)
                        break
                    t += m.deltaTime
                    if (m.event.eventType == MidiEventType.META && m.event.msb == MidiMetaType.TEMPO)
                        tempo = MidiMetaType.getTempo (m.event.extraData!!, m.event.extraDataOffset)
                }
                return v
            }
        }
    }

    val tracks : MutableList<MidiTrack> = ArrayList ()

    var deltaTimeSpec : Byte = 0

    var format : Byte = 0

    fun addTrack (track : MidiTrack) {
        this.tracks.add (track)
    }

    fun getMetaEventsOfType (metaType : Byte) : Iterable<MidiMessage> {
        if (format != 0.toByte())
            return SmfTrackMerger.merge (this).getMetaEventsOfType (metaType)
        return getMetaEventsOfType (tracks [0].messages, metaType).asIterable()
    }

    fun getTotalTicks () : Int {
        if (format != 0.toByte())
            return SmfTrackMerger.merge (this).getTotalTicks ()
        return tracks [0].messages.sumBy { m : MidiMessage -> m.deltaTime}
    }

    fun getTotalPlayTimeMilliseconds () : Int
    {
        if (format != 0.toByte())
            return SmfTrackMerger.merge (this).getTotalPlayTimeMilliseconds ()
        return getTotalPlayTimeMilliseconds (tracks [0].messages, deltaTimeSpec.toUnsigned())
    }

    fun getTimePositionInMillisecondsForTick (ticks : Int) : Int {
        if (format != 0.toByte())
            return SmfTrackMerger.merge (this).getTimePositionInMillisecondsForTick (ticks)
        return getPlayTimeMillisecondsAtTick (tracks [0].messages, ticks, deltaTimeSpec.toUnsigned())
    }

    init {
        this.format = 1
    }
}

class MidiTrack
{
    constructor ()
            : this (ArrayList<Midi2Event> ())

    constructor(messages : MutableList<Midi2Event>?)
    {
        if (messages == null)
            throw IllegalArgumentException ("null messages")
        this.messages = messages
    }

    var messages : MutableList<Midi2Event> = ArrayList ()

    fun addMessage (msg : Midi2Event)
    {
        messages.add (msg)
    }
}

class MidiCC
{
    companion object {

        const val BANK_SELECT = 0x00.toByte()
        const val MODULATION = 0x01.toByte()
        const val BREATH = 0x02.toByte()
        const val FOOT = 0x04.toByte()
        const val PORTAMENTO_TIME = 0x05.toByte()
        const val DTE_MSB = 0x06.toByte()
        const val VOLUME = 0x07.toByte()
        const val BALANCE = 0x08.toByte()
        const val PAN = 0x0A.toByte()
        const val EXPRESSION = 0x0B.toByte()
        const val EFFECT_CONTROL_1 = 0x0C.toByte()
        const val EFFECT_CONTROL_2 = 0x0D.toByte()
        const val GENERAL_1 = 0x10.toByte()
        const val GENERAL_2 = 0x11.toByte()
        const val GENERAL_3 = 0x12.toByte()
        const val GENERAL_4 = 0x13.toByte()
        const val BANK_SELECT_LSB = 0x20.toByte()
        const val MODULATION_LSB = 0x21.toByte()
        const val BREATH_LSB = 0x22.toByte()
        const val FOOT_LSB = 0x24.toByte()
        const val PORTAMENTO_TIME_LSB = 0x25.toByte()
        const val DTE_LSB = 0x26.toByte()
        const val VOLUME_LSB = 0x27.toByte()
        const val BALANCE_LSB = 0x28.toByte()
        const val PAN_LSB = 0x2A.toByte()
        const val EXPRESSION_LSB = 0x2B.toByte()
        const val EFFECT_1_LSB = 0x2C.toByte()
        const val EFFECT_2_LSB = 0x2D.toByte()
        const val GENERAL_1_LSB = 0x30.toByte()
        const val GENERAL_2_LSB = 0x31.toByte()
        const val GENERAL_3_LSB = 0x32.toByte()
        const val GENERAL_4_LSB = 0x33.toByte()
        const val HOLD = 0x40.toByte()
        const val PORTAMENTO_SWITCH = 0x41.toByte()
        const val SOSTENUTO = 0x42.toByte()
        const val SOFT_PEDAL = 0x43.toByte()
        const val LEGATO= 0x44.toByte()
        const val HOLD_2= 0x45.toByte()
        const val SOUND_CONTROLLER_1 = 0x46.toByte()
        const val SOUND_CONTROLLER_2 = 0x47.toByte()
        const val SOUND_CONTROLLER_3 = 0x48.toByte()
        const val SOUND_CONTROLLER_4 = 0x49.toByte()
        const val SOUND_CONTROLLER_5 = 0x4A.toByte()
        const val SOUND_CONTROLLER_6 = 0x4B.toByte()
        const val SOUND_CONTROLLER_7 = 0x4C.toByte()
        const val SOUND_CONTROLLER_8 = 0x4D.toByte()
        const val SOUND_CONTROLLER_9 = 0x4E.toByte()
        const val SOUND_CONTROLLER_10 = 0x4F.toByte()
        const val GENERAL_5= 0x50.toByte()
        const val GENERAL_6 = 0x51.toByte()
        const val GENERAL_7 = 0x52.toByte()
        const val GENERAL_8 = 0x53.toByte()
        const val PORTAMENTO_CONTROL = 0x54.toByte()
        const val RSD = 0x5B.toByte()
        const val EFFECT_1 = 0x5B.toByte()
        const val TREMOLO = 0x5C.toByte()
        const val EFFECT_2 = 0x5C.toByte()
        const val CSD = 0x5D.toByte()
        const val EFFECT_3 = 0x5D.toByte()
        const val CELESTE = 0x5E.toByte()
        const val EFFECT_4 = 0x5E.toByte()
        const val PHASER = 0x5F.toByte()
        const val EFFECT_5 = 0x5F.toByte()
        const val DTE_INCREMENT = 0x60.toByte()
        const val DTE_DECREMENT = 0x61.toByte()
        const val NRPN_LSB = 0x62.toByte()
        const val NRPN_MSB = 0x63.toByte()
        const val RPN_LSB = 0x64.toByte()
        const val RPN_MSB = 0x65.toByte()
        // Channel mode messages
        const val ALL_SOUND_OFF = 0x78.toByte()
        const val RESET_ALL_CONTROLLERS = 0x79.toByte()
        const val LOCAL_CONTROL = 0x7A.toByte()
        const val ALL_NOTES_OFF = 0x7B.toByte()
        const val OMNI_MODE_OFF = 0x7C.toByte()
        const val OMNI_MODE_ON = 0x7D.toByte()
        const val POLY_MODE_OFF = 0x7E.toByte()
        const val POLY_MODE_ON = 0x7F.toByte()
    }
}

class MidiPerNoteRCC // MIDI 2.0
{
    companion object {
        const val MODULATION = 0x01.toByte()
        const val BREATH = 0x02.toByte()
        const val PITCH_7_25 = 0x03.toByte()
        const val VOLUME = 0x07.toByte()
        const val BALANCE = 0x08.toByte()
        const val PAN = 0x0A.toByte()
        const val EXPRESSION = 0x0B.toByte()
        const val SOUND_CONTROLLER_1 = 0x46.toByte()
        const val SOUND_CONTROLLER_2 = 0x47.toByte()
        const val SOUND_CONTROLLER_3 = 0x48.toByte()
        const val SOUND_CONTROLLER_4 = 0x49.toByte()
        const val SOUND_CONTROLLER_5 = 0x4A.toByte()
        const val SOUND_CONTROLLER_6 = 0x4B.toByte()
        const val SOUND_CONTROLLER_7 = 0x4C.toByte()
        const val SOUND_CONTROLLER_8 = 0x4D.toByte()
        const val SOUND_CONTROLLER_9 = 0x4E.toByte()
        const val SOUND_CONTROLLER_10 = 0x4F.toByte()
        const val EFFECT_1_DEPTH = 0x5B.toByte() // Reverb Send Level by default
        const val EFFECT_2_DEPTH = 0x5C.toByte() // formerly Tremolo Depth
        const val EFFECT_3_DEPTH = 0x5D.toByte() // Chorus Send Level by default
        const val EFFECT_4_DEPTH = 0x5E.toByte() // formerly Celeste (Detune) Depth
        const val EFFECT_5_DEPTH = 0x5F.toByte() // formerly Phaser Depth
    }
}

class MidiRpnType
{
    companion object {

        const val PITCH_BEND_SENSITIVITY = 0.toByte()
        const val FINE_TUNING = 1.toByte()
        const val COARSE_TUNING = 2.toByte()
        const val TUNING_PROGRAM = 3.toByte()
        const val TUNING_BANK_SELECT = 4.toByte()
        const val MODULATION_DEPTH = 5.toByte()
    }
}

class MidiMetaType
{
    companion object {

        const val SEQUENCE_NUMBER = 0x00.toByte()
        const val TEXT = 0x01.toByte()
        const val COPYRIGHT = 0x02.toByte()
        const val TRACK_NAME= 0x03.toByte()
        const val INSTRUMENT_NAME = 0x04.toByte()
        const val LYRIC = 0x05.toByte()
        const val MARKER = 0x06.toByte()
        const val CUE = 0x07.toByte()
        const val CHANNEL_PREFIX= 0x20.toByte()
        const val END_OF_TRACK = 0x2F.toByte()
        const val TEMPO = 0x51.toByte()
        const val SMTPE_OFFSET = 0x54.toByte()
        const val TIME_SIGNATURE = 0x58.toByte()
        const val KEY_SIGNATURE = 0x59.toByte()
        const val SEQUENCER_SPECIFIC = 0x7F.toByte()

        const val DEFAULT_TEMPO = 500000

        fun getTempo (data : ByteArray, offset: Int) : Int
        {
            if (data.size < offset + 2)
                throw IndexOutOfBoundsException("data array must be longer than argument offset " + offset + " + 2")
            return (data[offset].toUnsigned() shl 16) + (data [offset + 1].toUnsigned() shl 8) + data [offset + 2]
        }

        fun getBpm (data : ByteArray, offset: Int) : Double
        {
            return 60000000.0 / getTempo(data, offset)
        }
    }
}

class MidiMessageType { // MIDI 2.0
    companion object {
        const val UTILITY : Byte = 0.toByte()
        const val SYSTEM : Byte = 1.toByte()
        const val MIDI1 : Byte = 2.toByte()
        const val SYSEX7 : Byte = 3.toByte()
        const val MIDI2 : Byte = 4.toByte()
        const val SYSEX8_MDS : Byte = 5.toByte()
    }
}

class MidiCIProtocolBytes { // MIDI 2.0
    companion object {
        const val TYPE: Byte = 0.toByte()
        const val VERSION: Byte = 1.toByte()
        const val EXTENSIONS: Byte = 2.toByte()
    }
}

class MidiCIProtocolType { // MIDI 2.0
    companion object {
        const val MIDI1 : Byte = 1.toByte()
        const val MIDI2 : Byte = 2.toByte()
    }
}

class MidiCIProtocolValue { // MIDI 2.0
    companion object {
        const val MIDI1 : Byte = 0.toByte()
        const val MIDI2_V1 : Byte = 0.toByte()
    }
}

class MidiCIProtocolExtensions { // MIDI 2.0
    companion object {
        const val JITTER : Byte = 1.toByte()
        const val LARGER : Byte = 2.toByte()
    }
}

class MidiPerNoteManagementFlags { // MIDI 2.0
    companion object {
        const val RESET : Byte = 1.toByte()
        const val DETACH : Byte = 2.toByte()
    }
}

class MidiEventType {
    companion object {

        // MIDI 2.0-specific
        const val JR_TIMESTAMP: Byte = 0x02.toByte()
        const val PER_NOTE_RCC: Byte = 0x00.toByte()
        const val PER_NOTE_ACC: Byte = 0x10.toByte()
        const val RPN: Byte = 0x20.toByte()
        const val NRPN: Byte = 0x30.toByte()
        const val RELATIVE_RPN: Byte = 0x40.toByte()
        const val RELATIVE_NRPN: Byte = 0x50.toByte()
        const val PER_NOTE_PITCH: Byte = 0x60.toByte()
        const val PER_NOTE_MANAGEMENT: Byte = 0xF0.toByte()

        // MIDI 1.0/2.0 common
        const val NOTE_OFF: Byte = 0x80.toByte()
        const val NOTE_ON: Byte = 0x90.toByte()
        const val PAF: Byte = 0xA0.toByte()
        const val CC: Byte = 0xB0.toByte()
        const val PROGRAM: Byte = 0xC0.toByte()
        const val CAF: Byte = 0xD0.toByte()
        const val PITCH: Byte = 0xE0.toByte()

        // MIDI 1.0-specific
        const val SYSEX: Byte = 0xF0.toByte()
        const val MTC_QUARTER_FRAME: Byte = 0xF1.toByte()
        const val SONG_POSITION_POINTER: Byte = 0xF2.toByte()
        const val SONG_SELECT: Byte = 0xF3.toByte()
        const val TUNE_REQUEST: Byte = 0xF6.toByte()
        const val SYSEX_END: Byte = 0xF7.toByte()
        const val MIDI_CLOCK: Byte = 0xF8.toByte()
        const val MIDI_TICK: Byte = 0xF9.toByte()
        const val MIDI_START: Byte = 0xFA.toByte()
        const val MIDI_CONTINUE: Byte = 0xFB.toByte()
        const val MIDI_STOP: Byte = 0xFC.toByte()
        const val ACTIVE_SENSE: Byte = 0xFE.toByte()
        const val RESET: Byte = 0xFF.toByte()
        const val META: Byte = 0xFF.toByte()
    }
}

class Midi2Event // MIDI 2.0 only
{
    companion object {

        fun convert (bytes : ByteArray, index : Int, size : Int) = sequence {
            var i = index
            val end = index +size
            while (i < end) {
                var size = getUmpSizeInBytes(bytes[i])
                when (size) {
                    16 -> yield(Midi2Event(read64(bytes, i), read64(bytes, i + 8)))
                    8 -> yield(Midi2Event(read64(bytes, i)))
                    else -> yield(Midi2Event(read32(bytes, i).toULong()))
                }
                i += size
            }
        }

        fun getUmpSizeInBytes (head: Byte) : Int {
            return when ((head.toInt() shr 4).toByte()) {
                MidiMessageType.SYSEX7, MidiMessageType.MIDI2 -> 8
                MidiMessageType.SYSEX8_MDS -> 16
                else -> 4
            }
        }

        fun read32(array: ByteArray, index: Int) : UInt =
                (array[index].toUInt() shl 24) + (array[index + 1].toUInt() shl 16) +
                (array[index + 2].toUInt() shl 8) + array[index + 3].toUInt()

        fun read64(array: ByteArray, index: Int) : ULong =
                (read32(array, index) shl 32).toULong() + read32(array, index + 4)
    }

    constructor (first64Bits : ULong)
    {
        this.first64Bits = first64Bits
    }

    constructor (first64Bits : ULong, second64Bits: ULong)
    {
        this.first64Bits = first64Bits
    }

    var first64Bits : ULong = 0.toULong()
    var second64Bits : ULong = 0.toULong()

    val typeAndGroupByte : Byte
        get() = ((first64Bits and 0xF0000000u) shr 56) .toByte()

    val messageType : Byte
        get() = ((first64Bits and 0xF0000000u) shr 60) .toByte()

    val statusByte : Byte
        get() = ((first64Bits and 0xFF0000u) shr 32) .toByte()

    val eventType : Byte
        get() =
            when (statusByte) {
                MidiEventType.META,
                MidiEventType.SYSEX,
                MidiEventType.SYSEX_END -> this.statusByte
                else ->(statusByte.toInt() and 0xF0).toByte()
            }

    val group : Byte
        get() = (typeAndGroupByte.toInt() and 0x0F).toByte()

    val channel : Byte
        get() = (statusByte.toInt() and 0x0F).toByte()

    // utility property to simplify unique channel on a "device"
    val groupAndChannel : Int
        get() = group * 0x100 + channel

    override fun toString () : String
    {
        return when (getUmpSizeInBytes((messageType * 16).toByte())) {
            16 -> String.format("[%16X:%16X]", first64Bits, second64Bits)
            8 -> String.format("[%16X]", first64Bits)
            else -> String.format("[%8X]", first64Bits)
        }
    }
}

class SmfWriter(var stream: OutputStream) {

    var disableRunningStatus : Boolean = false

    private fun writeShort (v: Short)
    {
        stream.writeByte ((v / 0x100).toByte())
        stream.writeByte ((v % 0x100).toByte())
    }

    private fun writeInt (v : Int)
    {
        stream.writeByte ((v / 0x1000000).toByte())
        stream.writeByte ((v / 0x10000 and 0xFF).toByte())
        stream.writeByte ((v / 0x100 and 0xFF).toByte())
        stream.writeByte ((v % 0x100).toByte())
    }

    fun writeMusic (music : MidiMusic)
    {
        writeHeader (music.format.toShort(), music.tracks.size.toShort(), music.deltaTimeSpec.toShort())
        for (track in music.tracks)
            writeTrack (track)
    }

    fun writeHeader (format : Short, tracks : Short, deltaTimeSpec : Short)
    {
        stream.write (byteArrayOf('M'.toByte(), 'T'.toByte(), 'h'.toByte(), 'd'.toByte()), 0, 4)
        writeShort (0)
        writeShort (6)
        writeShort (format)
        writeShort (tracks)
        writeShort (deltaTimeSpec)
    }

    var metaEventWriter : (Boolean, MidiMessage, OutputStream?) -> Int = SmfWriterExtension.DEFAULT_META_EVENT_WRITER

    fun writeTrack (track : MidiTrack)
    {
        stream.write (byteArrayOf('M'.toByte(), 'T'.toByte(), 'r'.toByte(), 'k'.toByte()), 0, 4)
        writeInt (getTrackDataSize (track))

        var running_status : Byte = 0

        for (e in track.messages) {
            write7BitVariableInteger (e.deltaTime)
            when (e.event.eventType) {
                MidiEventType.META -> metaEventWriter(false, e, stream)
                MidiEventType.SYSEX, MidiEventType.SYSEX_END -> {
                    stream.writeByte(e.event.eventType)
                    if (e.event.extraData != null) {
                        write7BitVariableInteger(e.event.extraDataLength)
                        stream.write(e.event.extraData, e.event.extraDataOffset, e.event.extraDataLength)
                    }
                }
                else -> {
                    if (disableRunningStatus || e.event.statusByte != running_status)
                        stream.writeByte(e.event.statusByte)
                    val len = MidiEvent.fixedDataSize (e.event.eventType)
                    stream.writeByte(e.event.msb)
                    if (len > 1)
                        stream.writeByte(e.event.lsb)
                    if (len > 2)
                        throw Exception ("Unexpected data size: $len")
                }
            }
            running_status = e.event.statusByte
        }
    }

    private fun getVariantLength (value : Int) : Int
    {
        if (value < 0)
            throw IllegalArgumentException (String.format ("Length must be non-negative integer: %d", value))
        if (value == 0)
            return 1
        var ret = 0
        var x : Int = value
        while (x != 0) {
            ret++
            x = x shr 7
        }
        return ret
    }

    private fun getTrackDataSize (track : MidiTrack ) : Int
    {
        var size = 0
        var runningStatus : Byte = 0
        for (e in track.messages) {
            // delta time
            size += getVariantLength (e.deltaTime)

            // arguments
            when (e.event.eventType) {
                MidiEventType.META -> size += metaEventWriter (true, e, null)
                MidiEventType.SYSEX, MidiEventType.SYSEX_END -> {
                    size++
                    if (e.event.extraData != null) {
                        size += getVariantLength(e.event.extraDataLength)
                        size += e.event.extraDataLength
                    }
                }
                else -> {
                    // message type & channel
                    if (disableRunningStatus || runningStatus != e.event.statusByte)
                        size++
                    size += MidiEvent.fixedDataSize(e.event.eventType)
                }
            }

            runningStatus = e.event.statusByte
        }
        return size
    }

    private fun write7BitVariableInteger (value : Int)
    {
        write7BitVariableInteger (value, false)
    }

    private fun write7BitVariableInteger (value : Int, shifted : Boolean)
    {
        if (value == 0) {
            stream.writeByte ((if (shifted) 0x80 else 0).toByte())
            return
        }
        if (value >= 0x80)
            write7BitVariableInteger (value shr 7, true)
        stream.writeByte (((value and 0x7F) + if (shifted) 0x80 else 0).toByte())
    }

}

class SmfWriterExtension
{
    companion object {

        val DEFAULT_META_EVENT_WRITER : (Boolean, MidiMessage, OutputStream?) -> Int = { b,m,o -> defaultMetaWriterFunc (b,m,o) }

        private fun defaultMetaWriterFunc (lengthMode : Boolean, e : MidiMessage , stream : OutputStream?) : Int
        {
            if (e.event.extraData == null || stream == null)
                return 0

            if (lengthMode) {
                // [0x00] 0xFF metaType size ... (note that for more than one meta event it requires step count of 0).
                val repeatCount : Int = e.event.extraDataLength / 0x7F
                if (repeatCount == 0)
                    return 3 + e.event.extraDataLength
                val mod : Int = e.event.extraDataLength % 0x7F
                return repeatCount * (4 + 0x7F) - 1 + if (mod > 0) 4 + mod else 0
            }

            var written = 0
            val total : Int = e . event.extraDataLength
            while (written < total) {
                if (written > 0)
                    stream.writeByte(0) // step
                stream.writeByte(0xFF.toByte())
                stream.writeByte(e.event.metaType)
                val size = Math.min(0x7F, total - written)
                stream.writeByte(size.toByte())
                stream.write(e.event.extraData, e.event.extraDataOffset + written, size)
                written += size
            }
            return 0
        }

        val vsqMetaTextSplitter : (Boolean, MidiMessage, OutputStream) -> Int = { b,m,o -> vsqMetaTextSplitterFunc (b,m,o) }

        private fun vsqMetaTextSplitterFunc (lengthMode : Boolean, e : MidiMessage , stream : OutputStream?) : Int
        {
            if (e.event.extraData == null)
                return 0

            // The split should not be applied to "Master Track"
            if (e.event.extraDataLength < 0x80) {
                return DEFAULT_META_EVENT_WRITER(lengthMode, e, stream)
            }

            if (lengthMode) {
                // { [0x00] 0xFF metaType DM:xxxx:... } * repeat + 0x00 0xFF metaType DM:xxxx:mod...
                // (note that for more than one meta event it requires step count of 0).
                val repeatCount = e.event.extraDataLength / 0x77
                if (repeatCount == 0)
                    return 11 + e.event.extraDataLength
                val mod = e.event.extraDataLength % 0x77
                return repeatCount * (12 + 0x77) - 1 + if (mod > 0) 12+mod else 0
            }

            if (stream == null)
                return 0


            var written = 0
            val total: Int = e.event.extraDataLength
            var idx = 0
            do {
                if (written > 0)
                    stream.writeByte(0.toByte()) // step
                stream.writeByte(0xFF.toByte())
                stream.writeByte(e.event.metaType)
                val size = Math.min(0x77, total - written)
                stream.writeByte((size + 8).toByte())
                stream.write(String.format("DM:{0:D04}:", idx++).toByteArray(), 0, 8)
                stream.write(e.event.extraData, e.event.extraDataOffset + written, size)
                written += size
            } while (written < total)
            return 0
        }
    }
}

class SmfReader(private var stream: InputStream) {

    var music = MidiMusic ()

    private val data = music

    fun read () {
        if (readByte() != 'M'.toByte()
                || readByte() != 'T'.toByte()
                || readByte() != 'h'.toByte()
                || readByte() != 'd'.toByte())
            throw parseError("MThd is expected")
        if (readInt32() != 6)
            throw parseError("Unexpected data size (should be 6)")
        data.format = readInt16().toByte()
        val tracks = readInt16()
        data.deltaTimeSpec = readInt16().toByte()
        for (i in 0 until tracks)
            data.tracks.add(readTrack())
    }

    private fun readTrack () : MidiTrack {
        val tr = MidiTrack()
        if (
                readByte() != 'M'.toByte()
                || readByte() != 'T'.toByte()
                || readByte() != 'r'.toByte()
                || readByte() != 'k'.toByte())
            throw parseError("MTrk is expected")
        val trackSize = readInt32()
        current_track_size = 0
        var total = 0
        while (current_track_size < trackSize) {
            val delta = readVariableLength()
            tr.messages.add(readMessage(delta))
            total += delta
        }
        if (current_track_size != trackSize)
            throw parseError("Size information mismatch")
        return tr
    }

    private var current_track_size : Int = 0
    private var running_status : Int = 0

    private fun readMessage (deltaTime : Int) : MidiMessage
    {
        val b = peekByte ().toUnsigned()
        running_status = if (b < 0x80) running_status else readByte ().toUnsigned()
        val len: Int
        when (running_status) {
            MidiEventType.SYSEX.toUnsigned(), MidiEventType.SYSEX_END.toUnsigned(), MidiEventType.META.toUnsigned() -> {
                val metaType = if (running_status == MidiEventType.META.toUnsigned()) readByte () else 0
                len = readVariableLength()
                val args = ByteArray(len)
                if (len > 0)
                    readBytes(args)
                return MidiMessage (deltaTime, MidiEvent (running_status, metaType.toUnsigned(), 0, args, 0, len))
            }
            else -> {
                var value = running_status
                value += readByte().toUnsigned() shl 8
                if (MidiEvent.fixedDataSize(running_status.toByte()) == 2.toByte())
                    value += readByte().toUnsigned() shl 16
                return MidiMessage (deltaTime, MidiEvent (value))
            }
        }
    }

    private fun readBytes (args : ByteArray)
    {
        current_track_size += args.size
        var start = 0
        if (peek_byte >= 0) {
            args [0] = peek_byte.toByte()
            peek_byte = -1
            start = 1
        }
        val len = stream.read (args, start, args.size - start)
        try {
            if (len < args.size - start)
                throw parseError (String.format ("The stream is insufficient to read %d bytes specified in the SMF message. Only %d bytes read.", args.size, len))
        } finally {
            stream_position += len
        }
    }

    private fun readVariableLength () : Int
    {
        var v = 0
        var i = 0
        while (i < 4) {
            val b = readByte ().toUnsigned()
            v = (v shl 7) + b
            if (b < 0x80)
                return v
            v -= 0x80
            i++
        }
        throw parseError ("Delta time specification exceeds the 4-byte limitation.")
    }

    private var peek_byte : Int = -1
    private var stream_position : Int = 0

    private fun peekByte () : Byte
    {
        if (peek_byte < 0)
            peek_byte = stream.read()
        if (peek_byte < 0)
            throw parseError ("Insufficient stream. Failed to read a byte.")
        return peek_byte.toByte()
    }

    private fun readByte () : Byte
    {
        try {

            current_track_size++
            if (peek_byte >= 0) {
                val b = peek_byte.toByte()
                peek_byte = -1
                return b
            }
            val ret = stream.read()
            if (ret < 0)
                throw parseError ("Insufficient stream. Failed to read a byte.")
            return ret.toByte()

        } finally {
            stream_position++
        }
    }

    private fun readInt16 () : Short
    {
        return ((readByte ().toUnsigned() shl 8) + readByte ().toUnsigned()).toShort()
    }

    private fun readInt32 () : Int
    {
        return (((readByte ().toUnsigned() shl 8) + readByte ().toUnsigned() shl 8) + readByte ().toUnsigned() shl 8) + readByte ().toUnsigned()
    }

    private fun parseError (msg : String) : Exception
    {
        return parseError (msg, null)
    }

    private fun parseError (msg : String, innerException : Exception? ) : Exception
    {
        if (innerException == null)
            throw SmfParserException (String.format ("$msg(at %s)", stream_position))
        else
            throw SmfParserException (String.format ("$msg(at %s)", stream_position), innerException)
    }
}

class SmfParserException : Exception
{
    constructor () : this ("SMF parser error")
    constructor (message : String) : super (message)
    constructor (message : String, innerException : Exception) : super (message, innerException)
}

class SmfTrackMerger(private var source: MidiMusic) {
    companion object {

        fun merge (source : MidiMusic) : MidiMusic
        {
            return SmfTrackMerger (source).getMergedMessages ()
        }
    }

    // FIXME: it should rather be implemented to iterate all
    // tracks with index to messages, pick the track which contains
    // the nearest event and push the events into the merged queue.
    // It's simpler, and costs less by removing sort operation
    // over thousands of events.
    private fun getMergedMessages () : MidiMusic {
        var l = ArrayList<MidiMessage>()

        for (track in source.tracks) {
            var delta = 0
            for (mev in track.messages) {
                delta += mev.deltaTime
                l.add(MidiMessage(delta, mev.event))
            }
        }

        if (l.size == 0) {
            val ret = MidiMusic()
            ret.deltaTimeSpec = source.deltaTimeSpec // empty (why did you need to sort your song file?)
            return ret
        }

        // Sort() does not always work as expected.
        // For example, it does not always preserve event
        // orders on the same channels when the delta time
        // of event B after event A is 0. It could be sorted
        // either as A->B or B->A.
        //
        // To resolve this ieeue, we have to sort "chunk"
        // of events, not all single events themselves, so
        // that order of events in the same chunk is preserved
        // i.e. [AB] at 48 and [CDE] at 0 should be sorted as
        // [CDE] [AB].

        val idxl = ArrayList<Int>(l.size)
        idxl.add(0)
        var prev = 0
        var i = 0
        while (i < l.size) {
            if (l[i].deltaTime != prev) {
                idxl.add(i)
                prev = l[i].deltaTime
            }
            i++
        }
        idxl.sortWith(Comparator{ i1, i2 -> l [i1].deltaTime-l [i2].deltaTime})

        // now build a new event list based on the sorted blocks.
        val l2 = ArrayList<MidiMessage>(l.size)
        var idx: Int
        i = 0
        while (i < idxl.size) {
            idx = idxl[i]
            prev = l[idx].deltaTime
            while (idx < l.size && l[idx].deltaTime == prev) {
                l2.add(l[idx])
                idx++
            }
            i++
        }
        l = l2

        // now messages should be sorted correctly.

        var waitToNext = l[0].deltaTime
        i = 0
        while (i < l.size - 1) {
            if (l[i].event.value != 0) { // if non-dummy
                val tmp = l[i + 1].deltaTime - l[i].deltaTime
                l[i] = MidiMessage(waitToNext, l[i].event)
                waitToNext = tmp
            }
            i++
        }
        l[l.size - 1] = MidiMessage(waitToNext, l[l.size - 1].event)

        val m = MidiMusic()
        m.deltaTimeSpec = source.deltaTimeSpec
        m.format = 0
        m.tracks.add(MidiTrack(l))
        return m
    }
}

class SmfTrackSplitter(var source: MutableList<MidiMessage>, deltaTimeSpec: Byte) {
    companion object {
        fun split(source: MutableList<MidiMessage>, deltaTimeSpec: Byte): MidiMusic {
            return SmfTrackSplitter(source, deltaTimeSpec).split()
        }
    }

    private var delta_time_spec = deltaTimeSpec
    private var tracks = HashMap<Int,SplitTrack> ()

    internal class SplitTrack(var trackID: Int) {

        var totalDeltaTime : Int
        var track : MidiTrack = MidiTrack ()

        fun addMessage (deltaInsertAt : Int, e : MidiMessage)
        {
            val e2 = MidiMessage (deltaInsertAt - totalDeltaTime, e.event)
            track.messages.add (e2)
            totalDeltaTime = deltaInsertAt
        }

        init {
            totalDeltaTime = 0
        }
    }

    private fun getTrack (track : Int) : SplitTrack
    {
        var t = tracks [track]
        if (t == null) {
            t = SplitTrack (track)
            tracks [track] = t
        }
        return t
    }

    // Override it to customize track dispatcher. It would be
    // useful to split note messages out from non-note ones,
    // to ease data reading.
    private fun getTrackID (e : MidiMessage) : Int
    {
        return when (e.event.eventType) {
            MidiEventType.META, MidiEventType.SYSEX, MidiEventType.SYSEX_END -> -1
            else -> e.event.channel.toUnsigned()
        }
    }

    private fun split () : MidiMusic
    {
        var totalDeltaTime = 0
        for (e in source) {
            totalDeltaTime += e.deltaTime
            val id: Int = getTrackID(e)
            getTrack(id).addMessage(totalDeltaTime, e)
        }

        val m = MidiMusic ()
        m.deltaTimeSpec = delta_time_spec
        for (t in tracks.values)
            m.tracks.add (t.track)
        return m
    }

    init {
        val mtr = SplitTrack (-1)
        tracks[-1] = mtr
    }
}

