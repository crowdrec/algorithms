#!/bin/bash
#!/bin/bash
# MyApp
#
# description: bla bla

case $1 in
    start)
	java -cp target/crowdrec-mahout-test-1.0-SNAPSHOT-jar-with-dependencies.jar dev.crowdrec.recs.mahout.ItembasedRec_batch /tmp /mnt/messaging/ > /tmp/mahout.log &
    ;;
    stop)
      pid=`ps aux | grep dev.crowdrec.recs.mahout.ItembasedRec_batch | grep java |awk '{print $2}'`
      kill -9 $pid
    ;;
esac
exit 0
