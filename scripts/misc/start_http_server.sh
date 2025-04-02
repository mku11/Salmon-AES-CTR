CURRDIR=$(pwd)

mkdir -p /tmp/salmon
cd /tmp/salmon
pip install rangehttpserver
python3 -m RangeHTTPServer --bind 127.0.0.1
cd $CURRDIR