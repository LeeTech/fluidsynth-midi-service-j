package name.atsushieno.fluidsynthmidideviceservicej

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Context
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), LifecycleObserver {

    lateinit var midi : FluidsynthMidiReceiver
    lateinit var midi_manager : MidiManager
    var midi_input : MidiInputPort? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onLifecycleStop () { disposeInput() }
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onLifecycleDestroy () { disposeInput() }

    fun disposeInput()
    {
        if (midi_input != null) {
            Log.d("FluidsynthMidiService", "[MainActivity] disposed midi input")
            midi_input?.close()
            midi_input = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycle.addObserver(this)

        this.button_direct.setOnClickListener {
            this.button_direct.isEnabled = false

            if (!this::midi.isInitialized)
                midi = FluidsynthMidiReceiver(this.applicationContext)
            play_client_midi(midi)

            this.button_direct.isEnabled = true
        }

        this.button_client.setOnClickListener {

            if (!this::midi_manager.isInitialized) {
                this.button_client.isEnabled = false

                midi_manager = this.applicationContext.getSystemService(Context.MIDI_SERVICE) as MidiManager
                val device = midi_manager.devices.first { d -> d.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT).equals("JFluidMidi") }
                midi_manager.openDevice(device, {
                    midi_input = it.openInputPort(0)
                    this.runOnUiThread { this.button_client.isEnabled = true }
                    play_client_midi(midi_input!!)
                }, null)
            }
            else
                play_client_midi(midi_input!!)
        }
    }

    fun play_client_midi (midi : MidiReceiver)
    {
        midi.send(arrayOf(0xB0.toByte(), 7, 120).toByteArray(), 0, 3)
        midi.send(arrayOf(0xC0.toByte(), 48).toByteArray(), 0, 2)
        midi.send(arrayOf(0x90.toByte(), 0x40, 120).toByteArray(), 0, 3)
        midi.send(arrayOf(0x90.toByte(), 0x44, 120).toByteArray(), 0, 3)
        midi.send(arrayOf(0x90.toByte(), 0x47, 120).toByteArray(), 0, 3)
        AsyncTask.execute {
            Thread.sleep(1000)
            midi.send(arrayOf(0x90.toByte(), 0x49, 120).toByteArray(), 0, 3)
            AsyncTask.execute {
                Thread.sleep(1000)
                midi.send(arrayOf(0x80.toByte(), 0x40, 0).toByteArray(), 0, 3)
                midi.send(arrayOf(0x80.toByte(), 0x44, 0).toByteArray(), 0, 3)
                midi.send(arrayOf(0x80.toByte(), 0x47, 0).toByteArray(), 0, 3)
                midi.send(arrayOf(0x80.toByte(), 0x49, 0).toByteArray(), 0, 3)
            }
        }
    }
}
