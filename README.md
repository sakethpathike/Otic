Otic lets you stream your Android device's microphone input to the local network using
`ServerSocket` and Foreground Service.

Otic isn't a receiver, so you must have a custom receiver to receive the audio from the Otic client.

The easiest way to do this is to create a virtual microphone using PipeWire/PulseAudio on your Linux
machine and source the audio via GStreamer:

```bash
#!/bin/bash

VIRTUAL_MIC_SINK="OticMic_Sink"
VIRTUAL_MIC_SOURCE="OticMic"
PHONE_IP="<FIND_IP_FROM_OTIC>"
PORT="<FIND_PORT_FROM_OTIC>"

SINK_MODULE_ID=""
SOURCE_MODULE_ID=""

cleanup() {
    if [ -n "$SOURCE_MODULE_ID" ]; then
        pactl unload-module "$SOURCE_MODULE_ID" 2>/dev/null
    fi
    if [ -n "$SINK_MODULE_ID" ]; then
        pactl unload-module "$SINK_MODULE_ID" 2>/dev/null
    fi
    exit 0
}

trap cleanup EXIT INT TERM

SINK_MODULE_ID=$(pactl load-module module-null-sink \
    sink_name="$VIRTUAL_MIC_SINK" \
    sink_properties=device.description="Otic_Receiver_Sink")

if [ -z "$SINK_MODULE_ID" ]; then
    echo "Failed to create virtual sink"
    exit 1
fi

SOURCE_MODULE_ID=$(pactl load-module module-remap-source \
    master="$VIRTUAL_MIC_SINK.monitor" \
    source_name="$VIRTUAL_MIC_SOURCE" \
    source_properties=device.description="Otic_Receiver")

if [ -z "$SOURCE_MODULE_ID" ]; then
    echo "Failed to create virtual microphone"
    exit 1
fi

echo "Virtual microphone ready"
echo "Connecting to $PHONE_IP:$PORT"

gst-launch-1.0 -v \
    tcpclientsrc host="$PHONE_IP" port="$PORT" ! \
    rawaudioparse use-sink-caps=false format=pcm pcm-format=s16le sample-rate=48000 num-channels=1 ! \
    audioconvert ! \
    audioresample ! \
    pulsesink device="$VIRTUAL_MIC_SINK"

echo "Stream stopped"
```

Note that `sample-rate` must be `48000` and the `format` must be `s16le` (`pcm`), as these are the
exact values used by the app. `<FIND_IP_FROM_OTIC>` and `<FIND_PORT_FROM_OTIC>` should also be replaced with valid values.

Always start streaming from the app first, and then run the script. You might also want to use
`nohup` since the stream terminates when you exit the script.

## Download

[<img src="https://github.com/user-attachments/assets/a50513b3-dbf8-48c1-bff8-1f4215fefbb9" alt="Get it on GitHub" height="80">](https://github.com/sakethpathike/Otic/releases)
