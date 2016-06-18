/*******************************************************************************
 * Copyright 2014 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogicgames.packr;

import java.io.File;
import java.io.IOException;

/**
 * Some file utility wrappers to check for function results, and to raise exceptions in case of error.
 */
class PackrFileUtils {

	static void mkdirs(File path) throws IOException {
		if (!path.mkdirs()) {
			throw new IOException("Can't create folder(s): " + path);
		}
	}

	static void chmodX(File path) {
		if (!path.setExecutable(true)) {
			System.err.println("Warning! Failed setting executable flag for: " + path);
		}
	}

	static void delete(File path) throws IOException {
		if (!path.delete()) {
			throw new IOException("Can't delete file or folder: " + path);
		}
	}

}
