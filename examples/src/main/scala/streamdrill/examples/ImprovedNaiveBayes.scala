package streamdrill.examples

import streamdrill.client.StreamDrillClient

/**
 * Example to use streamdrill for some non-trivial machine learning.
 *
 * Collects statistics in an online fashion for the improved Naive Bayes method based on
 * the paper
 *
 * J. D. M. Rennie, L. Shih, J. Teevan, D. R. Karger, "Tackling the Poor
 * Assumptions of Naive Bayes Text Classifiers", ICML 2003.
 *
 * User: mikio
 * Date: 1/28/13
 * Time: 4:41 PM
 */
class ImprovedNaiveBayes(streamdrill: StreamDrillClient, alpha0: Double, alpha: Seq[Double]) {
  var labels = Set[String]()

  val WORD_DOCUMENT_COUNTS = "wordDocumentCounts"
  val WORD_LABEL_COUNTS = "wordLabelCounts"
  val ALL = "*"

  // One trend for the word document counts used for the IDF score
  streamdrill.create(WORD_DOCUMENT_COUNTS, "word", 1000000, Seq("day"))

  // One trend to store transformed label-word counts.
  // Also uses label ALL to track global counts, and word ALL to track
  // global counts.
  streamdrill.create(WORD_LABEL_COUNTS, "label:word", 1000000, Seq("day"))

  /**
   * update statistics in streamdrill
   *
   * @param tokens sequence of words of which the text consists
   * @param label class of the text
   */
  def update(tokens: Seq[String], label: String) {
    if (!labels.contains(label)) {
      labels += label
    }

    val counts = wordCounts(tokens)
    val words = counts.keySet.toIndexedSeq
    val numwords = counts.size

    // step 1: counts => log(counts + 1)
    val logScores = counts.map(wc => (wc._1 -> math.log(wc._2 + 1.0)))
    // step 2: tf-idf transform
    val tfidfscores = logScores.map(ws => (ws._1 -> ws._2 * math.log(numwords / docfreq(ws._1))))
    // step 3: normalize
    val norm = math.sqrt(tfidfscores.map(ws => ws._2 * ws._2).sum)
    val normalizedScores = tfidfscores.map(ws => ws._1 -> ws._2 / norm)

    // update the transformed counts.
    normalizedScores.foreach(ws => streamdrill.update(WORD_LABEL_COUNTS, Seq(label, ws._1), value=Some(ws._2)))
    // update global transformed counts.
    normalizedScores.foreach(ws => streamdrill.update(WORD_LABEL_COUNTS, Seq(ALL, ws._1), value=Some(ws._2)))

    // update global sum counts.
    val sumOfScores = normalizedScores.map(_._2).sum
    streamdrill.update(WORD_LABEL_COUNTS, Seq(label, ALL), value=Some(sumOfScores))
    streamdrill.update(WORD_LABEL_COUNTS, Seq(ALL, ALL), value=Some(sumOfScores))

    // update document frequencies
    words.foreach(w => streamdrill.update(WORD_DOCUMENT_COUNTS, Seq(w)))
  }

  def wordCounts(words: Seq[String]): Map[String, Int] = {
    var result = Map[String,Int]()
    for (word <- words)
      result += (word -> (result.getOrElse(word, 0) + 1))
    result
  }

  def docfreq(word: String): Double = {
    streamdrill.score(WORD_DOCUMENT_COUNTS, Seq(word))
  }

  def wordLabelScore(label: String, word: String): Double = {
    streamdrill.score(WORD_LABEL_COUNTS, Seq(label, word))
  }

  /**
   * Return a prediction for a text.
   */
  def predict(text: Seq[String]): (String, Double) = {
    val counts = wordCounts(text)

    val words = counts.keySet.toIndexedSeq
    val is = (0 until words.length).toArray

    // Collect scores for all class but the current one.
    val allScores = is.map(i => wordLabelScore(ALL, words(i)))
    val allSumOfScores = wordLabelScore(ALL, ALL)
    val weights = labels.map {
      l =>
        l -> is.map(i =>
          (allScores(i) - wordLabelScore(l, words(i)) + alpha(i)) /
          (allSumOfScores - wordLabelScore(ALL, words(i)) + alpha0))
    }.toMap

    // predict
    val labelScores = labels.map {
      l =>
        l -> is.map(i => counts(words(i)) * weights(l)(i)).sum
    }.toMap

    val winner = labels.minBy(l => labelScores(l))
    (winner, labelScores(winner))
  }
}
