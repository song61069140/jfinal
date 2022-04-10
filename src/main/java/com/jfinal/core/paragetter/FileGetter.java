/**
 * Copyright (c) 2011-2023, 玛雅牛 (myaniu AT gmail.com).
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
package com.jfinal.core.paragetter;

import java.io.File;

import com.jfinal.core.Action;
import com.jfinal.core.Controller;
import com.jfinal.upload.UploadFile;

public class FileGetter extends ParaGetter<File> {

	public FileGetter(String parameterName,String defaultValue) {
		super(parameterName,null);
	}

	@Override
	public File get(Action action, Controller c) {
		String parameterName = this.getParameterName();
		UploadFile uf = null;
		if(parameterName.isEmpty()){
			uf = c.getFile();
		}else{
			uf = c.getFile(parameterName);
		}
		if(uf != null){
			return uf.getFile();
		}
		return null;
	}

	@Override
	protected File to(String v) {
		return null;
	}

}
