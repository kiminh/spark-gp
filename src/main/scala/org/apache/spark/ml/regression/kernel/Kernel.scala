package org.apache.spark.ml.regression.kernel

import breeze.linalg.{diag, DenseMatrix => BDM, DenseVector => BDV}
import breeze.numerics._
import org.apache.spark.ml.linalg.{Vector, Vectors}


trait Kernel {
  var hyperparameters: Vector

  def setTrainingVectors(vectors: Array[Vector]): this.type

  def trainingKernel(): BDM[Double]

  def trainingKernelAndDerivative(): (BDM[Double], Array[BDM[Double]])

  def crossKernel(test: Array[Vector]): BDM[Double]
}

class TrainingVectorsNotInitializedException
  extends Exception("setTrainingVectors method should have been called first")

class RBFKernel(sigma: Double, regularization: Double = 0) extends Kernel {
  var hyperparameters : Vector = Vectors.dense(Array(sigma))

  private def getSigma() = hyperparameters(0)

  private var squaredDistances: Option[BDM[Double]] = None

  private var trainOption: Option[Array[Vector]] = None

  def this() = this(1, 1e-6)

  override def setTrainingVectors(vectors: Array[Vector]): this.type = {
    trainOption = Some(vectors)
    val sqd = BDM.zeros[Double](vectors.length, vectors.length)
    for (i <- vectors.indices; j <- 0 to i) {
      val dist = Vectors.sqdist(vectors(i), vectors(j))
      sqd(i, j) = dist
      sqd(j, i) = dist
    }

    squaredDistances = Some(sqd)
    this
  }

  override def trainingKernel(): BDM[Double] = {
    val nonRegularized = exp(squaredDistances.getOrElse(throw new TrainingVectorsNotInitializedException)
      / (-2d * getSigma()*getSigma()))

    nonRegularized + diag(BDV[Double]((0 until nonRegularized.cols)
      .map(_ => regularization).toArray))
  }

  override def trainingKernelAndDerivative(): (BDM[Double], Array[BDM[Double]]) = {
    val sqd = squaredDistances.getOrElse(throw new TrainingVectorsNotInitializedException)

    val kernel = trainingKernel()
    val derivative = (sqd *:* kernel) / (getSigma() * getSigma() * getSigma())

    (kernel, Array(derivative))
  }

  override def crossKernel(test: Array[Vector]): BDM[Double] = {
    val train = trainOption.getOrElse(throw new TrainingVectorsNotInitializedException)
    val values = train.flatMap(trainVector =>
      test.map(testVector =>
        Vectors.sqdist(trainVector, testVector)/ (-2d * getSigma()*getSigma()))
    )

    exp(BDM.create(test.length, train.length, values))
  }
}