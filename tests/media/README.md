# Controller media fixture

`controller.mp4` is a 20-second synthetic colour/motion and 440 Hz tone fixture, generated for controller seek/fullscreen tests. It contains no provider media or account data. Reproduce with FFmpeg:

```sh
ffmpeg -f lavfi -i 'testsrc2=size=320x180:rate=24' -f lavfi -i 'sine=frequency=440:sample_rate=48000' -t 20 -c:v libx264 -profile:v baseline -pix_fmt yuv420p -g 24 -crf 30 -c:a aac -b:a 64k -movflags +faststart controller.mp4
```

Set `CONTROLLER_MEDIA=1` when starting `scripts/mock-server.mjs` to serve this MP4. HLS/TS fixtures remain unchanged. The older `test.mp4` produced an emulator AAC decode error after seeking to 16 seconds; it remains available to reproduce that separate media compatibility issue. Synthetic success is not evidence for real provider codecs or physical Android Auto compatibility.


## Throttled seek fixture

Generate a longer synthetic file outside the source package (FFmpeg):

```sh
ffmpeg -f lavfi -i 'testsrc2=size=320x180:rate=24' -f lavfi -i 'sine=frequency=440:sample_rate=48000' -t 180 -c:v libx264 -profile:v baseline -pix_fmt yuv420p -g 24 -crf 25 -c:a aac -b:a 64k -movflags +faststart work/seek-fixture.mp4
node scripts/seek-fixture.mjs
```

Set FIXTURE_PORT=8794 and CDP_PORT to the debug harness forwarding port, then run `node tests/seek-native.mjs`. The server throttles normal responses and can stall or ignore nonzero byte ranges. Only generated media and demo credentials are used. It exposes numeric request/transfer counters to verify that a jump went beyond the initial buffered region. No provider media is included.
