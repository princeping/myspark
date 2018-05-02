package come.prince.spark.bayes

import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.feature.{HashingTF, IDF, Tokenizer}
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.sql.{Row, SparkSession}

/**
  * 贝叶斯文本分类测试
  * Created by princeping on 2018/4/24.
  */
object TestNaiveBayes {

  case class RawDataRecord(category: String, text: String)

  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.WARN)

    val spark = SparkSession.builder().master("local[*]").getOrCreate()

    val input = spark.sparkContext.textFile("E:\\分词\\C000008.txt")

    val srcRDD = input.map(line => {
      val array = line.split(",")
      RawDataRecord(array(0), array(1))
    })

    val splits = srcRDD.randomSplit(Array(0.7, 0.3))

    import spark.implicits._

    val trainingDF = splits(0).toDF
    val testDF = splits(1).toDF

    //将词语转化成数组
    val tokenizer = new Tokenizer().setInputCol("text").setOutputCol("words")
    val wordsData = tokenizer.transform(trainingDF)
    println("output1:")
    wordsData.select($"category", $"text", $"words").show()

    //计算每个词在文档中的频率
    val hashingTF = new HashingTF().setNumFeatures(500000).setInputCol("words").setOutputCol("rawFeatures")
    val featurizedData = hashingTF.transform(wordsData)
    println("output2:")
    featurizedData.select($"category", $"words", $"rawFeatures").show()

    //计算每个词的TF-IDF
    val idf = new IDF().setInputCol("rawFeatures").setOutputCol("features")
    val iDFModel = idf.fit(featurizedData)
    val rescaleData = iDFModel.transform(featurizedData)
    println("output3:")
    rescaleData.select($"category", $"features").show()

    //转换成Bayes的输入格式
    val trainDataRdd = rescaleData.select($"category", $"features").map{
      case Row(label: String, features: Vector) =>
        LabeledPoint(label.toDouble, Vectors.dense(features.toArray))
    }
    println("output4:")
    trainDataRdd.show()

    //训练模型
    val model = NaiveBayes.train(trainDataRdd.rdd, lambda = 1.0, modelType = "multinomial")

    //测试数据集，做同样的特征表示及格式转化
    val testWordsData = tokenizer.transform(testDF)
    val testFeaturizedData = hashingTF.transform(testWordsData)
    val testRescaledData = iDFModel.transform(testFeaturizedData)
    val testDataRdd = testRescaledData.select($"category", $"features").map{
      case Row(label: String, features: Vector) =>
        LabeledPoint(label.toDouble, Vectors.dense(features.toArray))
    }

    //对测试数据集使用训练模型进行分类测试
    val testPredictionAndLabel = testDataRdd.map(line => (model.predict(line.features), line.label))

    //统计分类准确率
    val testAccuracy = 1.0 * testPredictionAndLabel.filter(x => x._1 == x._2).count() / testDataRdd.count()
    println("output5:")
    println(testAccuracy)

//    featurizedData.select($"category", $"words", $"rawFeatures").rdd.saveAsTextFile("C:\\Users\\Administrator\\Desktop\\prince\\out1")
//    rescaleData.select($"category", $"features").rdd.saveAsTextFile("C:\\Users\\Administrator\\Desktop\\prince\\out2")

    spark.stop()
  }
}
