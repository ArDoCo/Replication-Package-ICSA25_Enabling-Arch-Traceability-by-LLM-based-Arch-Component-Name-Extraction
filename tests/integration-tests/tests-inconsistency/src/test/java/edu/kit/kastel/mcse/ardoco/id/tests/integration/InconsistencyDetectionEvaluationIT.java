/* Licensed under MIT 2021-2024. */
package edu.kit.kastel.mcse.ardoco.id.tests.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.core.api.inconsistency.ModelInconsistency;
import edu.kit.kastel.mcse.ardoco.core.api.models.ModelElement;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.ArchitectureModel;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.architecture.ArchitectureItem;
import edu.kit.kastel.mcse.ardoco.core.api.output.ArDoCoResult;
import edu.kit.kastel.mcse.ardoco.core.common.util.FilePrinter;
import edu.kit.kastel.mcse.ardoco.core.tests.TestUtil;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.GoldStandardProject;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.Project;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.results.EvaluationResults;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.results.ExpectedResults;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.results.calculator.ResultCalculatorUtil;
import edu.kit.kastel.mcse.ardoco.id.tests.integration.inconsistencyhelper.HoldBackRunResultsProducer;
import edu.kit.kastel.mcse.ardoco.id.types.MissingModelInstanceInconsistency;
import edu.kit.kastel.mcse.ardoco.tlr.models.connectors.generators.architecture.pcm.PcmExtractor;

/**
 * Integration test that evaluates the inconsistency detection capabilities of ArDoCo. Runs on the projects that are defined in the enum {@link Project}.
 * <p>
 * Currently, the focus lies on detecting elements that are mentioned in the text but are not represented in the model. For this, we run an evaluation that
 * holds back (removes) one element from the model. This way, we know that there is a missing element and the trace links to this element (in the gold standard)
 * are the spots of inconsistency then. We run this multiple times so each element was held back once.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InconsistencyDetectionEvaluationIT {
    private static final Logger logger = LoggerFactory.getLogger(InconsistencyDetectionEvaluationIT.class);

    private static final String OUTPUT = "target/testout";
    public static final String DIRECTORY_NAME = "ardoco_eval_id";

    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final Map<GoldStandardProject, ArDoCoResult> arDoCoResults = new LinkedHashMap<>();

    /**
     * Tests the inconsistency detection for missing model elements on all {@link Project projects}.
     * <p>
     * NOTE: if you only want to test a specific project, you can simply set up the EnumSource. For more details, see
     * <a href="https://www.baeldung.com/parameterized-tests-junit-5#3-enum">here</a>
     * Example: add ", names = { "BIGBLUEBUTTON" }" to EnumSource However, make sure to revert this before you commit and push!
     *
     * @param goldStandardProject Project that gets inserted automatically with the enum {@link Project}.
     */
    @DisplayName("Evaluating MME-Inconsistency Detection")
    @ParameterizedTest(name = "Evaluating MME-Inconsistency for {0}")
    @EnumSource(Project.class)
    @Order(1)
    void missingModelElementInconsistencyIT(GoldStandardProject goldStandardProject) {
        runMissingModelElementInconsistencyEval(goldStandardProject, goldStandardProject.getExpectedInconsistencyResults());
    }

    protected void runMissingModelElementInconsistencyEval(GoldStandardProject goldStandardProject, ExpectedResults expectedInconsistencyResults) {
        logger.info("Start evaluation of MME-inconsistency for {}", goldStandardProject.getProjectName());
        Map<ModelElement, ArDoCoResult> runs = produceRuns(goldStandardProject);

        var results = calculateEvaluationResults(goldStandardProject, runs);

        EvaluationResults<String> weightedResults = ResultCalculatorUtil.calculateMicroAverageResults(results.toImmutable());

        logResultsMissingModelInconsistency(goldStandardProject, weightedResults, expectedInconsistencyResults);
        checkResults(weightedResults, expectedInconsistencyResults);

        writeOutResults(goldStandardProject, results, runs);
    }

    /**
     * Tests the baseline approach that reports a missing model element inconsistency for each sentence that is not traced to a model element. This test is
     * enabled by providing the environment variable "testBaseline" with any value.
     *
     * @param goldStandardProject Project that gets inserted automatically with the enum {@link Project}.
     */
    @EnabledIfEnvironmentVariable(named = "testBaseline", matches = ".*")
    @DisplayName("Evaluating MME-Inconsistency Detection Baseline")
    @ParameterizedTest(name = "Evaluating Baseline for {0}")
    @EnumSource(Project.class)
    @Order(5)
    void missingModelElementInconsistencyBaselineIT(GoldStandardProject goldStandardProject) {
        runMissingModelElementInconsistencyBaselineEval(goldStandardProject);
    }

    protected void runMissingModelElementInconsistencyBaselineEval(GoldStandardProject goldStandardProject) {
        logger.info("Start evaluation of MME-inconsistency baseline for {}", goldStandardProject.getProjectName());

        HoldBackRunResultsProducer holdBackRunResultsProducer = new HoldBackRunResultsProducer();
        Map<ModelElement, ArDoCoResult> runs = holdBackRunResultsProducer.produceHoldBackRunResults(goldStandardProject, true);

        Assertions.assertTrue(runs != null && !runs.isEmpty());

        var results = calculateEvaluationResults(goldStandardProject, runs);

        var weightedResults = ResultCalculatorUtil.calculateWeightedAverageResults(results.toImmutable());

        if (logger.isInfoEnabled()) {
            String name = goldStandardProject.getProjectName() + " missing model inconsistency";
            TestUtil.logResults(logger, name, weightedResults);
        }
    }

    /**
     * Tests the inconsistency detection for undocumented model elements on all {@link Project projects}.
     *
     * @param goldStandardProject Project that gets inserted automatically with the enum {@link Project}.
     */
    @DisplayName("Evaluate Inconsistency Analyses For MissingTextForModelElementInconsistencies")
    @ParameterizedTest(name = "Evaluating UME-inconsistency for {0}")
    @EnumSource(Project.class)
    @Order(10)
    void missingTextInconsistencyIT(GoldStandardProject goldStandardProject) {
        runMissingTextInconsistencyEval(goldStandardProject);
    }

    private void runMissingTextInconsistencyEval(GoldStandardProject goldStandardProject) {
        var projectResults = arDoCoResults.get(goldStandardProject);
        if (projectResults == null) {
            produceRuns(goldStandardProject);
            projectResults = arDoCoResults.get(goldStandardProject);
        }
        Assertions.assertNotNull(projectResults, "No results found.");

        MutableList<String> expectedInconsistentModelElements = goldStandardProject.getMissingTextForModelElementGoldStandard();
        var inconsistentModelElements = projectResults.getAllModelInconsistencies().collect(ModelInconsistency::getModelInstanceUid).toList();
        var results = TestUtil.compareInconsistencies(projectResults, inconsistentModelElements.toImmutable(), expectedInconsistentModelElements.toImmutable());

        String name = goldStandardProject.getProjectName() + " missing text inconsistency";
        TestUtil.logExplicitResults(logger, name, results);
        writeOutResults(goldStandardProject, results);
    }

    private Map<ModelElement, ArDoCoResult> produceRuns(GoldStandardProject goldStandardProject) {
        HoldBackRunResultsProducer holdBackRunResultsProducer = getHoldBackRunResultsProducer();

        Map<ModelElement, ArDoCoResult> runs = holdBackRunResultsProducer.produceHoldBackRunResults(goldStandardProject, false);

        ArDoCoResult baseArDoCoResult = runs.get(null);
        saveOutput(goldStandardProject, baseArDoCoResult);
        arDoCoResults.put(goldStandardProject, baseArDoCoResult);
        return runs;
    }

    protected HoldBackRunResultsProducer getHoldBackRunResultsProducer() {
        return new HoldBackRunResultsProducer();
    }

    private MutableList<EvaluationResults<String>> calculateEvaluationResults(GoldStandardProject goldStandardProject, Map<ModelElement, ArDoCoResult> runs) {

        Map<ModelElement, EvaluationResults<String>> results = Maps.mutable.empty();

        for (var run : runs.entrySet()) {
            ModelElement modelInstance = run.getKey();
            ArDoCoResult arDoCoResult = run.getValue();
            var runEvalResults = evaluateRun(goldStandardProject, modelInstance, arDoCoResult);
            if (runEvalResults != null) {
                results.put(modelInstance, runEvalResults);
            } else {
                logger.error("Evaluation results for {} are null.", modelInstance);
            }
        }
        return Lists.mutable.ofAll(results.values());
    }

    private EvaluationResults<String> evaluateRun(GoldStandardProject goldStandardProject, ModelElement removedElement, ArDoCoResult arDoCoResult) {
        var modelId = arDoCoResult.getModelIds().getFirst();

        ImmutableList<MissingModelInstanceInconsistency> inconsistencies = arDoCoResult.getInconsistenciesOfTypeForModel(modelId,
                MissingModelInstanceInconsistency.class);
        if (removedElement == null) {
            // base case
            return null;
        }

        var goldStandard = goldStandardProject.getTlrGoldStandard(getPcmModel(goldStandardProject));
        var expectedLines = goldStandard.getSentencesWithElement(removedElement).distinct().collect(Object::toString);
        var actualSentences = inconsistencies.collect(MissingModelInstanceInconsistency::sentence).distinct().collect(Object::toString);

        return calculateEvaluationResults(arDoCoResult, expectedLines, actualSentences);
    }

    private static EvaluationResults<String> calculateEvaluationResults(ArDoCoResult arDoCoResult, ImmutableList<String> expectedLines,
            ImmutableList<String> actualSentences) {
        return TestUtil.compareInconsistencies(arDoCoResult, actualSentences, expectedLines);
    }

    private static ArchitectureModel getPcmModel(GoldStandardProject goldStandardProject) {
        return new PcmExtractor(goldStandardProject.getModelFile().getAbsolutePath()).extractModel();
    }

    private void logResultsMissingModelInconsistency(GoldStandardProject goldStandardProject, EvaluationResults<String> weightedAverageResult,
            ExpectedResults expectedResults) {
        if (logger.isInfoEnabled()) {
            String name = goldStandardProject.getProjectName() + " missing model inconsistency";
            TestUtil.logExtendedResultsWithExpected(logger, this, name, weightedAverageResult, expectedResults);
        }
    }

    private void checkResults(EvaluationResults<String> results, ExpectedResults expectedResults) {
        Assertions.assertAll(//
                () -> Assertions.assertTrue(results.precision() >= expectedResults.precision(), "Precision " + results
                        .precision() + " is below the expected minimum value " + expectedResults.precision()), //
                () -> Assertions.assertTrue(results.recall() >= expectedResults.recall(), "Recall " + results
                        .recall() + " is below the expected minimum value " + expectedResults.recall()), //
                () -> Assertions.assertTrue(results.f1() >= expectedResults.f1(), "F1 " + results
                        .f1() + " is below the expected minimum value " + expectedResults.f1()), () -> Assertions.assertTrue(results
                                .accuracy() >= expectedResults.accuracy(), "Accuracy " + results
                                        .accuracy() + " is below the expected minimum value " + expectedResults.accuracy()), //
                () -> Assertions.assertTrue(results.phiCoefficient() >= expectedResults.phiCoefficient(), "Phi coefficient " + results
                        .phiCoefficient() + " is below the expected " + "minimum value " + expectedResults.phiCoefficient()));
    }

    private void writeOutResults(GoldStandardProject goldStandardProject, List<EvaluationResults<String>> results, Map<ModelElement, ArDoCoResult> runs) {
        var outputs = createOutput(goldStandardProject, results, runs);
        var outputBuilder = outputs.getOne();
        var detailedOutputBuilder = outputs.getTwo();

        Path outputPath = Path.of(OUTPUT);
        Path idEvalPath = outputPath.resolve(DIRECTORY_NAME);
        try {
            Files.createDirectories(outputPath);
            Files.createDirectories(idEvalPath);
        } catch (IOException e) {
            logger.warn("Could not create directories.", e);
        }

        String projectFileName = "inconsistencies_" + goldStandardProject.getProjectName() + ".txt";
        var filename = idEvalPath.resolve(projectFileName).toFile().getAbsolutePath();
        FilePrinter.writeToFile(filename, outputBuilder.toString());

        String detailedProjectFileName = "detailed_" + projectFileName;
        var detailedFilename = idEvalPath.resolve(detailedProjectFileName).toFile().getAbsolutePath();
        FilePrinter.writeToFile(detailedFilename, detailedOutputBuilder.toString());
    }

    private void writeOutResults(GoldStandardProject goldStandardProject, EvaluationResults<String> results) {
        Path outputPath = Path.of(OUTPUT);
        Path idEvalPath = outputPath.resolve(DIRECTORY_NAME);
        try {
            Files.createDirectories(outputPath);
            Files.createDirectories(idEvalPath);
        } catch (IOException e) {
            logger.warn("Could not create directories.", e);
        }

        var outputBuilder = createStringBuilderWithHeader(goldStandardProject);
        outputBuilder.append(TestUtil.createResultLogString("Inconsistent Model Elements", results));
        outputBuilder.append(LINE_SEPARATOR);
        outputBuilder.append("Number of True Positives: ").append(results.truePositives().size());
        outputBuilder.append(LINE_SEPARATOR);
        outputBuilder.append("Number of False Positives: ").append(results.falsePositives().size());
        outputBuilder.append(LINE_SEPARATOR);
        outputBuilder.append("Number of False Negatives: ").append(results.falseNegatives().size());

        String projectFileName = "inconsistentModelElements_" + goldStandardProject.getProjectName() + ".txt";
        var filename = idEvalPath.resolve(projectFileName).toFile().getAbsolutePath();
        FilePrinter.writeToFile(filename, outputBuilder.toString());
    }

    private static void saveOutput(GoldStandardProject goldStandardProject, ArDoCoResult arDoCoResult) {
        Objects.requireNonNull(goldStandardProject);
        Objects.requireNonNull(arDoCoResult);

        String projectName = goldStandardProject.getProjectName();
        var outputDir = Path.of(OUTPUT);
        var filename = projectName + ".txt";

        var outputFileTLR = outputDir.resolve("traceLinks_" + filename).toFile();
        FilePrinter.writeTraceabilityLinkRecoveryOutput(outputFileTLR, arDoCoResult);
        var outputFileID = outputDir.resolve("inconsistencyDetection_" + filename).toFile();
        FilePrinter.writeInconsistencyOutput(outputFileID, arDoCoResult);
    }

    private static Pair<StringBuilder, StringBuilder> createOutput(GoldStandardProject goldStandardProject, List<EvaluationResults<String>> results,
            Map<ModelElement, ArDoCoResult> runs) {
        StringBuilder outputBuilder = createStringBuilderWithHeader(goldStandardProject);
        var resultCalculatorStringBuilderPair = inspectResults(results, runs, outputBuilder);
        var resultCalculator = resultCalculatorStringBuilderPair.getOne();
        outputBuilder.append(getOverallResultsString(resultCalculator));
        var detailedOutputBuilder = resultCalculatorStringBuilderPair.getTwo();
        return Tuples.pair(outputBuilder, detailedOutputBuilder);
    }

    private static String getOverallResultsString(MutableList<EvaluationResults<String>> results) {
        StringBuilder outputBuilder = new StringBuilder();
        outputBuilder.append("###").append(LINE_SEPARATOR);
        var weightedAverageResults = ResultCalculatorUtil.calculateWeightedAverageResults(results.toImmutable());
        var resultString = TestUtil.createResultLogString("### OVERALL RESULTS ###" + LINE_SEPARATOR + "Weighted" + " Average", weightedAverageResults);
        outputBuilder.append(resultString);
        outputBuilder.append(LINE_SEPARATOR);
        return outputBuilder.toString();
    }

    private static StringBuilder createStringBuilderWithHeader(GoldStandardProject goldStandardProject) {
        StringBuilder outputBuilder = new StringBuilder();
        outputBuilder.append("### ").append(goldStandardProject.getProjectName()).append(" ###");
        outputBuilder.append(LINE_SEPARATOR);
        return outputBuilder;
    }

    private static Pair<MutableList<EvaluationResults<String>>, StringBuilder> inspectResults(List<EvaluationResults<String>> results,
            Map<ModelElement, ArDoCoResult> runs, StringBuilder outputBuilder) {
        var detailedOutputBuilder = new StringBuilder();
        MutableList<EvaluationResults<String>> resultsWithWeight = Lists.mutable.empty();
        int counter = 0;
        for (var run : runs.entrySet()) {
            ArDoCoResult arDoCoResult = run.getValue();
            ArchitectureItem instance = (ArchitectureItem) run.getKey();
            if (instance == null) {
                inspectBaseCase(outputBuilder, arDoCoResult);
            } else {
                outputBuilder.append("###").append(LINE_SEPARATOR);
                detailedOutputBuilder.append("###").append(LINE_SEPARATOR);
                outputBuilder.append("Removed Instance: ").append(instance.getName());
                detailedOutputBuilder.append("Removed Instance: ").append(instance.getName());
                outputBuilder.append(LINE_SEPARATOR);
                detailedOutputBuilder.append(LINE_SEPARATOR);
                var result = results.get(counter++);
                var resultString = String.format(Locale.ENGLISH, "Precision: %.3f, Recall: %.3f, F1: %" + ".3f, Accuracy: %.3f, Phi Coef.: %.3f", result
                        .precision(), result.recall(), result.f1(), result.accuracy(), result.phiCoefficient());
                outputBuilder.append(resultString);
                detailedOutputBuilder.append(resultString);
                inspectRun(outputBuilder, detailedOutputBuilder, resultsWithWeight, arDoCoResult, result);
            }

            outputBuilder.append(LINE_SEPARATOR);
        }

        return Tuples.pair(resultsWithWeight, detailedOutputBuilder);
    }

    private static void inspectRun(StringBuilder outputBuilder, StringBuilder detailedOutputBuilder, MutableList<EvaluationResults<String>> allResults,
            ArDoCoResult arDoCoResult, EvaluationResults<String> result) {
        var truePositives = result.truePositives();
        appendResults(truePositives, detailedOutputBuilder, "True Positives", arDoCoResult, outputBuilder);

        var falsePositives = result.falsePositives();
        appendResults(falsePositives, detailedOutputBuilder, "False Positives", arDoCoResult, outputBuilder);

        var falseNegatives = result.falseNegatives();
        appendResults(falseNegatives, detailedOutputBuilder, "False Negatives", arDoCoResult, outputBuilder);
        allResults.add(result);
    }

    private static void appendResults(List<String> resultList, StringBuilder detailedOutputBuilder, String type, ArDoCoResult arDoCoResult,
            StringBuilder outputBuilder) {
        resultList = sortIntegerStrings(resultList);
        detailedOutputBuilder.append(LINE_SEPARATOR).append("== ").append(type).append(" ==");
        detailedOutputBuilder.append(LINE_SEPARATOR).append(createDetailedOutputString(arDoCoResult, resultList));
        outputBuilder.append(LINE_SEPARATOR).append(type).append(": ").append(listToString(resultList));
    }

    private static void inspectBaseCase(StringBuilder outputBuilder, ArDoCoResult data) {
        var initialInconsistencies = getInitialInconsistencies(data);
        outputBuilder.append("Initial Inconsistencies: ").append(initialInconsistencies.size());
        var initialInconsistenciesSentences = initialInconsistencies.collect(MissingModelInstanceInconsistency::sentence)
                .toSortedSet()
                .collect(Object::toString);
        outputBuilder.append(LINE_SEPARATOR).append(listToString(initialInconsistenciesSentences));
    }

    private static String createDetailedOutputString(ArDoCoResult result, List<String> sentenceNumbers) {
        var outputBuilder = new StringBuilder();

        if (sentenceNumbers.isEmpty()) {
            return outputBuilder.append("None").append(LINE_SEPARATOR).toString();
        }

        for (var inconsistentSentence : result.getInconsistentSentences()) {
            int sentenceNumber = inconsistentSentence.sentence().getSentenceNumberForOutput();
            var sentenceNumberString = Integer.toString(sentenceNumber);
            if (sentenceNumbers.contains(sentenceNumberString)) {
                outputBuilder.append(inconsistentSentence.getInfoString());
                outputBuilder.append(LINE_SEPARATOR);
            }
        }

        return outputBuilder.toString();
    }

    private static List<String> sortIntegerStrings(List<String> list) {
        return list.stream().map(Integer::parseInt).sorted().map(Object::toString).toList();
    }

    private static String listToString(List<?> truePositives) {
        return truePositives.stream().map(Object::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    private static ImmutableList<MissingModelInstanceInconsistency> getInitialInconsistencies(ArDoCoResult arDoCoResult) {
        var id = arDoCoResult.getModelIds().getFirst();
        return arDoCoResult.getInconsistenciesOfTypeForModel(id, MissingModelInstanceInconsistency.class);
    }
}
