package come.prince.spark.streaming

import org.apache.log4j.{Level, Logger}
import com.typesafe.config.ConfigFactory
import come.prince.spark.util.InternalRedisClient
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, HasOffsetRanges, KafkaUtils}
import org.apache.spark.streaming.kafka010.LocationStrategies
import redis.clients.jedis.Pipeline
/**
  * flume->kafka->spark streaming->redis测试
  * Created by princeping on 2018/4/17.
  */
object TestSparkStreaming {

  Logger.getLogger("org").setLevel(Level.WARN)

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder.appName("KafkaWriteRedis").master("local[*]").getOrCreate()
    val sparkContext = spark.sparkContext
    val ssc = new StreamingContext(sparkContext, Seconds(1))

    implicit val conf = ConfigFactory.load

    val topic = conf.getString("kafka.topics")
    val partition: Int = 0 //测试topic只有一个分区

    //配置kafka
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> conf.getString("kafka.brokers"),
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> conf.getString("kafka.group"),
      "auto.offset.reset" -> "none",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    //配置redis
    val maxTotal = 10
    val maxIdle = 10
    val minIdle = 1
    val redisHost = "192.168.1.24"
    val redisPort = 6379
    val redisTimeout = 30000
    val dbDefaultIndex = 8
    InternalRedisClient.makePool(redisHost, redisPort, redisTimeout, maxTotal, maxIdle, minIdle)

    //从redis获取上一次保存的offset
    val jedis = InternalRedisClient.getPool.getResource
    jedis.select(dbDefaultIndex)
    val topic_partition_key = topic + "_" + partition
    var lastOffset = 0l
    val lastSavedOffset = jedis.get(topic_partition_key)

    if (lastSavedOffset != null) {
      try {
        lastOffset = lastSavedOffset.toLong
      } catch {
        case e: Exception => println(e.getMessage)
          println("get lastSavedOffset error, lastSavedOffset from redis [" + lastSavedOffset + "]")
          System.exit(1)
      }
    }
    InternalRedisClient.getPool.returnResourceObject(jedis)

    println("lastOffset from redis ->" + lastOffset)

    //设置每个分区起始的offset
    val fromOffsets = Map{
      new TopicPartition(topic, partition) -> lastOffset
    }

    //使用direct api 创建 stream
    val stream = KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Assign[String, String](fromOffsets.keys.toList, kafkaParams, fromOffsets))

    //批处理
    stream.foreachRDD(rdd => {
      val offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      val result = processLogs(rdd)
      println("====================== Total" + result.length + "events in this batch ..")

      val jedis = InternalRedisClient.getPool.getResource
      val p1: Pipeline = jedis.pipelined()
      p1.select(dbDefaultIndex)
      p1.multi()//开启事务

      result.foreach(record => {
        //增加小时总pv
        val pv_by_hour_key = "pv_" + record.hour
        p1.incr(pv_by_hour_key)

        //增加网站小时pv
        val site_pv_by_hour_key = "site_pv_" + record.site_id + "_" + record.hour
        p1.incr(site_pv_by_hour_key)

        //使用set保存当天的uv
        val uv_by_day_key = "uv_" + record.hour.substring(0, 10)
        p1.sadd(uv_by_day_key, record.user_id)
      })

      //更新offset
      offsetRanges.foreach(offsetRange => {
        println("partition:" + offsetRange.partition + "fromOffset:" + offsetRange.fromOffset +
        "untilOffset:" + offsetRange.untilOffset)
        val topic_partition_key = offsetRange.topic + "_" + offsetRange.partition
        p1.set(topic_partition_key, offsetRange.untilOffset + "")
      })

      p1.exec()//提交事务
      p1.sync()//关闭pipeline

      InternalRedisClient.getPool.returnResourceObject(jedis)
    })


    case class MyRecord(hour: String, user_id: String, site_id: String)

    def processLogs(messages: RDD[ConsumerRecord[String, String]]): Array[MyRecord] = {
      messages.map(_.value()).flatMap(parseLog).collect()
    }

    //解析每条日志，生成MyRecord
    def parseLog(line: String): Option[MyRecord] = {
      val array: Array[String] = line.split("\\|", -1)
      try {
        val hour = array(0).substring(0, 13).replace("T", "-")
        val uri = array(2).split("")
        val user_id = uri(1)
        val site_id = uri(3)
        return Some(MyRecord(hour,user_id,site_id))
      }catch {
        case e: Exception => println(e.getMessage)
      }
      return None
    }

    ssc.start()
    ssc.awaitTermination()
  }
}
