package com.cfs.worker;

import com.cfs.traversal.FileTask;

import java.io.IOException;

@FunctionalInterface
public interface FileAction {
    void apply(FileTask task) throws IOException;
}
