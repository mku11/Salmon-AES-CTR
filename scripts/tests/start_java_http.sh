CURRDIR=$(pwd)

mkdir -p /tmp/salmon
cd /tmp/salmon
pip install rangehttpserver
python3 -m RangeHTTPServer
cd $CURRDIR