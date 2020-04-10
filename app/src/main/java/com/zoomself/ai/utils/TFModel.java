//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.zoomself.ai.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Interpreter.Options;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.SupportPreconditions;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.Map;

public class TFModel {
    private final Interpreter interpreter;
    private final String modelPath;
    private final MappedByteBuffer byteModel;
    private final GpuDelegate gpuDelegate;
    private final Device device;
    private final int threadCount;

    @NonNull
    public MappedByteBuffer getData() {
        return this.byteModel;
    }

    public Device getDevice() {
        return this.device;
    }

    public int getThreadCount() {
        return this.threadCount;
    }

    @NonNull
    public String getPath() {
        return this.modelPath;
    }

    public int[] getOutputTensorShape(int outputIndex) {
        return this.interpreter.getOutputTensor(outputIndex).shape();
    }

    public DataType getOutputTensorDataType(int outputIndex) {
        return this.interpreter.getOutputTensor(outputIndex).dataType();
    }

    public DataType getInputTensorDataType(int inputIndex) {
        return this.interpreter.getInputTensor(inputIndex).dataType();
    }

    public int[] getInputTensorShape(int inputIndex) {
        return this.interpreter.getInputTensor(inputIndex).shape();
    }

    public void run(@NonNull Object[] inputs, @NonNull Map<Integer, Object> outputs) {
        this.interpreter.runForMultipleInputsOutputs(inputs, outputs);
    }

    public void run(Object input, Object output) {
        this.interpreter.run(input, output);
    }

    public void close() {
        if (this.interpreter != null) {
            this.interpreter.close();
        }

        if (this.gpuDelegate != null) {
            this.gpuDelegate.close();
        }

    }

    private TFModel(@NonNull String modelPath, @NonNull MappedByteBuffer byteModel, TFModel.Device device, int numThreads) {
        SupportPreconditions.checkNotNull(byteModel, "Model file cannot be null.");
        SupportPreconditions.checkNotEmpty(modelPath, "Model path in the asset folder cannot be empty.");
        this.modelPath = modelPath;
        this.byteModel = byteModel;
        this.device = device;
        this.threadCount = numThreads;
        Options interpreterOptions = new Options();
        this.gpuDelegate = device == TFModel.Device.GPU ? new GpuDelegate() : null;
        switch (device) {
            case NNAPI:
                interpreterOptions.setUseNNAPI(true);
                break;
            case GPU:
                interpreterOptions.addDelegate(this.gpuDelegate);
            case CPU:
        }

        interpreterOptions.setNumThreads(numThreads);
        this.interpreter = new Interpreter(byteModel, interpreterOptions);
    }

    public static class Builder {
        private TFModel.Device device;
        private int numThreads;
        private final String modelPath;
        private final MappedByteBuffer byteModel;

        public Builder(@NonNull Context context, @NonNull String modelPath) throws IOException {
            this.device = TFModel.Device.CPU;
            this.numThreads = 1;
            this.modelPath = modelPath;
            this.byteModel = FileUtil.loadMappedFile(context, modelPath);
        }

        @NonNull
        public TFModel.Builder setDevice(TFModel.Device device) {
            this.device = device;
            return this;
        }

        @NonNull
        public TFModel.Builder setNumThreads(int numThreads) {
            this.numThreads = numThreads;
            return this;
        }

        @NonNull
        public TFModel build() {
            return new TFModel(this.modelPath, this.byteModel, this.device, this.numThreads);
        }
    }

    public static enum Device {
        CPU,
        NNAPI,
        GPU;

        private Device() {
        }
    }
}
