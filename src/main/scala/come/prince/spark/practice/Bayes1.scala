package come.prince.spark.practice

import org.apache.log4j.{Level, Logger}
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.sql.SparkSession

/**
  * 贝叶斯入门
  * Created by princeping on 2018/4/23.
  */
object Bayes1 {
  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.WARN)

    val spark = SparkSession.builder().master("local").getOrCreate()
    val input = spark.sparkContext.textFile("C:\\Users\\Administrator\\Desktop\\input.txt")

    val allData = input.map(line => {
      val colData = line.split(",")
      LabeledPoint(colData(0).toDouble, Vectors.dense(colData(1).split(" ").map(_.toDouble)))
    })

    val nbTrained = NaiveBayes.train(allData)

    val people = "6 130 8"
    val vec = Vectors.dense(people.split(" ").map(_.toDouble))

    val nbPredict = nbTrained.predict(vec)

    println("预测此人性别是：" + (
      if(nbPredict == 0)
        "女"
      else
        "男"
        ))
  }
}
