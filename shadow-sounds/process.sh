#!/usr/bin/env bash
# Builds Mansoority's custom sounds from CC0 creature recordings (OpenGameArt):
#   - "Monster Sound Effects Pack" by Ogrebane (CC0)   https://opengameart.org/content/monster-sound-effects-pack
#   - "Monster snarls" by Darsycho (CC0)                https://opengameart.org/content/monster-snarls
# Everything is pitched down, slowed, filtered and drenched in reverb so it sounds like something huge and wrong.
set -e
mkdir -p src out spec
cd src
curl -sL -o pack.zip "https://opengameart.org/sites/default/files/monster_sfx_pack.zip"
unzip -o -q pack.zip -d pack || true
for f in monster-snarls_0 monster-snarls-2_0 monsters-snarls-3_0 monster-snarl-attack monster-snarl-5; do
  curl -sL -o "$f.ogg" "https://opengameart.org/sites/default/files/$f.ogg"
done
find . -type f | sort
cd ..
# normalise every source to wav mono 44.1k
i=0
for f in $(find src -type f \( -iname '*.ogg' -o -iname '*.mp3' -o -iname '*.wav' \) | sort); do
  ffmpeg -loglevel error -y -i "$f" -ac 1 -ar 44100 "src/n_$(printf %02d $i).wav" && echo "n_$(printf %02d $i) <- $f"
  i=$((i+1))
done
fx() {  # in out pitch(cents) tempo reverb lowpass
  sox "$1" "$2" pitch "$3" tempo "$4" lowpass "$6" reverb "$5" 50 100 100 10 2 gain -n -2 fade q 0.05 -0 0.35
}
n=0
for f in src/n_*.wav; do
  d=$(soxi -D "$f")
  # scream: pitched down hard, wide and huge
  fx "$f" "out/scream_$n.wav" -500 0.85 70 6000
  # breath / growl: low, slow, close
  fx "$f" "out/breath_$n.wav" -900 0.7 35 2200
  # a quieter, far-off, cavernous version for distant cries
  sox "$f" "out/far_$n.wav" pitch -700 tempo 0.8 lowpass 1500 reverb 95 80 100 100 40 6 gain -n -9 fade q 0.2 -0 0.8
  n=$((n+1))
done
for w in out/*.wav; do
  b=$(basename "$w" .wav)
  ffmpeg -loglevel error -y -i "$w" -c:a libvorbis -q:a 5 "out/$b.ogg"
  sox "$w" -n spectrogram -x 600 -y 200 -o "spec/$b.png" -t "$b ($(soxi -D "$w")s)"
  rm "$w"
done
ls -la out
