F=$1
B=`basename $F`
D=$B.dot
P=$B.png

~/ltsmin/src/spinja2lts-gsea --dot=$D $F
dot -Tpng -o$P $D
