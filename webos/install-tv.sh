#!/bin/sh
# Installs dist/*.ipk on a webOS TV in Developer Mode and launches it.
# Plain ssh instead of ares-install: the CLI's ssh library can't sign in to the TV's old OpenSSH 6.1.
# Usage: ./install-tv.sh [tv-ip]   (key: ~/.ssh/tv_webos from `ares-novacom --device tv --getkey`)
set -e
TV=${1:-192.168.1.25}
APP=io.github.nindzyago.torrentodrive.webos
IPK=$(ls -t "$(dirname "$0")"/dist/${APP}_*.ipk | head -1)
NAME=$(basename "$IPK")
SSH="ssh -p 9922 -i $HOME/.ssh/tv_webos -o IdentitiesOnly=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PubkeyAcceptedAlgorithms=+ssh-rsa -o HostKeyAlgorithms=+ssh-rsa -o LogLevel=ERROR prisoner@$TV"

$SSH "cat > /media/developer/temp/$NAME" < "$IPK"
# luna-send-pub prints nothing without a terminal, hence -tt
$SSH -tt "luna-send-pub -n 6 -w 60000 luna://com.webos.appInstallService/dev/install \
  '{\"id\":\"$APP\",\"ipkUrl\":\"/media/developer/temp/$NAME\",\"subscribe\":true}'; exit" | grep -o '"state":"[^"]*"\|"errorText":"[^"]*"' | uniq
sleep 3
$SSH -tt "luna-send-pub -n 1 luna://com.webos.applicationManager/launch '{\"id\":\"$APP\"}'; exit"
