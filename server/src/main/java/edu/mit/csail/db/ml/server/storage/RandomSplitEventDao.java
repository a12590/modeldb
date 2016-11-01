package edu.mit.csail.db.ml.server.storage;

import jooq.sqlite.gen.Tables;
import jooq.sqlite.gen.tables.records.DataframeRecord;
import jooq.sqlite.gen.tables.records.DataframesplitRecord;
import jooq.sqlite.gen.tables.records.EventRecord;
import jooq.sqlite.gen.tables.records.RandomspliteventRecord;
import modeldb.*;
import org.jooq.DSLContext;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RandomSplitEventDao {
  public static RandomSplitEventResponse store(RandomSplitEvent rse, DSLContext ctx) {
    DataframeRecord oldDf = DataFrameDao.store(rse.oldDataFrame, rse.experimentRunId, ctx);


    RandomspliteventRecord rseRec = ctx.newRecord(Tables.RANDOMSPLITEVENT);
    rseRec.setId(null);
    rseRec.setInputdataframeid(oldDf.getId());
    rseRec.setRandomseed(rse.seed);
    rseRec.setExperimentrun(rse.experimentRunId);
    rseRec.store();

    EventRecord ev = EventDao.store(rseRec.getId(), "random split", rse.experimentRunId, ctx);

    List<DataframeRecord> splitDfs = rse
      .splitDataFrames
      .stream()
      .map(df -> DataFrameDao.store(df, rse.experimentRunId, ctx))
      .collect(Collectors.toList());

    List<Integer> splitIds = IntStream
      .range(0, rse.splitDataFrames.size())
      .map(ind -> {
        DataframesplitRecord splRec = ctx.newRecord(Tables.DATAFRAMESPLIT);
        splRec.setId(null);
        splRec.setSpliteventid(rseRec.getId());
        splRec.setWeight(rse.weights.get(ind).floatValue());
        splRec.setDataframeid(splitDfs.get(ind).getId());
        splRec.setExperimentrun(rse.experimentRunId);
        splRec.store();
        return splRec.getId();
      })
      .boxed()
      .collect(Collectors.toList());

    // Store a TransformEvent for each split. This allows us to preserve the ancestor chain of DataFrames.
    DataFrame oldDataFrame = rse.oldDataFrame.setId(oldDf.getId());
    Transformer rseTransformer = new Transformer(-1, Collections.emptyList(), "RandomSplitTransformer", "");
    IntStream.range(0, rse.splitDataFrames.size())
      .forEach(index -> {
        DataFrame splitDataFrame = rse.splitDataFrames.get(index).setId(splitDfs.get(index).getId());
        TransformEvent te = new TransformEvent(
          oldDataFrame,
          splitDataFrame,
          rseTransformer,
          Collections.emptyList(),
          Collections.emptyList(),
          rse.experimentRunId
        );
        TransformEventDao.store(te, ctx, false);
      });

    return new RandomSplitEventResponse(oldDf.getId(), splitIds, ev.getId());
  }
}
