package edu.mit.csail.db.ml.server.storage;

import jooq.sqlite.gen.Tables;
import jooq.sqlite.gen.tables.records.FeatureRecord;
import jooq.sqlite.gen.tables.records.LinearmodelRecord;
import jooq.sqlite.gen.tables.records.LinearmodeltermRecord;
import jooq.sqlite.gen.tables.records.ModelobjectivehistoryRecord;
import modeldb.LinearModel;
import modeldb.LinearModelTerm;
import org.jooq.DSLContext;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LinearModelDao {
  private static LinearmodeltermRecord store(int linearModelId, int termIndex, LinearModelTerm term, DSLContext ctx) {
    LinearmodeltermRecord termRec = ctx.newRecord(Tables.LINEARMODELTERM);
    termRec.setId(null);
    termRec.setModel(linearModelId);
    termRec.setTermindex(termIndex);
    termRec.setCoefficient(term.coefficient);
    if (term.isSetTStat()) {
      termRec.setTstat(term.getTStat());
    }
    if (term.isSetStdErr()) {
      termRec.setStderr(term.getStdErr());
    }
    if (term.isSetPValue()) {
      termRec.setPvalue(term.getPValue());
    }
    termRec.store();
    termRec.getId();
    return termRec;
  }

  public static boolean store(int modelId, LinearModel model, DSLContext ctx) {
    // Check if the modelId exists, and if it doesn't, return false.
    if (ctx
      .select(Tables.TRANSFORMER.ID)
      .from(Tables.TRANSFORMER)
      .where(Tables.TRANSFORMER.ID.eq(modelId))
      .fetchOne() == null) {
      return false;
    }

    // Store the LinearModel.
    LinearmodelRecord lmRec = ctx.newRecord(Tables.LINEARMODEL);
    lmRec.setId(null);
    lmRec.setModel(modelId);
    if (model.isSetRmse()) {
      lmRec.setRmse(model.getRmse());
    }
    if (model.isSetExplainedVariance()) {
      lmRec.setExplainedvariance(model.getExplainedVariance());
    }
    if (model.isSetR2()) {
      lmRec.setR2(model.getR2());
    }
    lmRec.store();
    lmRec.getId();

    // Store the intercept term.
    if (model.isSetInterceptTerm()) {
      store(modelId, 0, model.getInterceptTerm(), ctx);
    }

    // Store the featureTerms.
    IntStream
      .range(0, model.featureTerms.size())
      .forEach(i -> store(modelId, i + 1, model.featureTerms.get(i), ctx));

    // Store the objective history.
    if (model.isSetObjectiveHistory()) {
      IntStream.range(0, model.objectiveHistory.size())
        .forEach(i -> {
          double objectiveValue = model.objectiveHistory.get(i);
          ModelobjectivehistoryRecord mohRec = ctx.newRecord(Tables.MODELOBJECTIVEHISTORY);
          mohRec.setId(null);
          mohRec.setModel(modelId);
          mohRec.setIteration(i + 1);
          mohRec.setObjectivevalue(objectiveValue);
          mohRec.store();
          mohRec.getId();
        });
    }

    // Now we will update the features for the model so that the
    // importance is equal to the absolute value of the coefficient.

    // Get the features for the model.
    List<FeatureRecord> features = ctx.selectFrom(Tables.FEATURE)
      .where(Tables.FEATURE.TRANSFORMER.eq(modelId))
      .fetch()
      .stream()
      .collect(Collectors.toList());

    // Delete all the features for the models.
    ctx.deleteFrom(Tables.FEATURE)
      .where(Tables.FEATURE.TRANSFORMER.eq(modelId));

    // Store the features again.
    features.forEach(ft -> {
      ft.setImportance(Math.abs(model.featureTerms.get(ft.getFeatureindex()).coefficient));
      ft.store();
      ft.getImportance();
    });

    return true;
  }
}