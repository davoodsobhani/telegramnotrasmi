/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ir.mehdi.messenger.exoplayer2.source.chunk;

import ir.mehdi.messenger.exoplayer2.C;
import ir.mehdi.messenger.exoplayer2.Format;
import ir.mehdi.messenger.exoplayer2.extractor.DefaultExtractorInput;
import ir.mehdi.messenger.exoplayer2.extractor.DefaultTrackOutput;
import ir.mehdi.messenger.exoplayer2.extractor.ExtractorInput;
import ir.mehdi.messenger.exoplayer2.upstream.DataSource;
import ir.mehdi.messenger.exoplayer2.upstream.DataSpec;
import ir.mehdi.messenger.exoplayer2.util.Util;

import java.io.IOException;

/**
 * A {@link BaseMediaChunk} for chunks consisting of a single raw sample.
 */
public final class SingleSampleMediaChunk extends BaseMediaChunk {

  private final Format sampleFormat;

  private volatile int bytesLoaded;
  private volatile boolean loadCanceled;
  private volatile boolean loadCompleted;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded.
   * @param trackFormat See {@link #trackFormat}.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The index of the chunk.
   */
  public SingleSampleMediaChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long startTimeUs, long endTimeUs,
      int chunkIndex, Format sampleFormat) {
    super(dataSource, dataSpec, trackFormat, trackSelectionReason, trackSelectionData, startTimeUs,
        endTimeUs, chunkIndex);
    this.sampleFormat = sampleFormat;
  }

  @Override
  public boolean isLoadCompleted() {
    return loadCompleted;
  }

  @Override
  public long bytesLoaded() {
    return bytesLoaded;
  }

  // Loadable implementation.

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public boolean isLoadCanceled() {
    return loadCanceled;
  }

  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public void load() throws IOException, InterruptedException {
    DataSpec loadDataSpec = Util.getRemainderDataSpec(dataSpec, bytesLoaded);
    try {
      // Create and open the input.
      long length = dataSource.open(loadDataSpec);
      if (length != C.LENGTH_UNSET) {
        length += bytesLoaded;
      }
      ExtractorInput extractorInput = new DefaultExtractorInput(dataSource, bytesLoaded, length);
      DefaultTrackOutput trackOutput = getTrackOutput();
      trackOutput.formatWithOffset(sampleFormat, 0);
      // Load the sample data.
      int result = 0;
      while (result != C.RESULT_END_OF_INPUT) {
        bytesLoaded += result;
        result = trackOutput.sampleData(extractorInput, Integer.MAX_VALUE, true);
      }
      int sampleSize = bytesLoaded;
      trackOutput.sampleMetadata(startTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
    } finally {
      dataSource.close();
    }
    loadCompleted = true;
  }

}