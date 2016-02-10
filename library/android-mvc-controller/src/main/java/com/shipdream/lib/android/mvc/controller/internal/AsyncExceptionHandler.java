/*
 * Copyright 2016 Kejun Xia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shipdream.lib.android.mvc.controller.internal;

import java.util.concurrent.ExecutorService;

/**
 * Interface to handle exceptions of {@link AsyncTask}
 *
 * @deprecated see {@link BaseControllerImpl#runTask(Object, ExecutorService, Task, Monitor.Callback)} and
 * {@link BaseControllerImpl#runTask(Object, Task)} and {@link BaseControllerImpl#runTask(Object, Task, Monitor.Callback)}
 */
public interface AsyncExceptionHandler {
    /**
     * Method to handle exception of {@link AsyncTask}
     * @param exception Exception occurs during executing the {@link AsyncTask}
     */
    void handleException(Exception exception);
}
