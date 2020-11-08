/**
 * Copyright (c) 2011-2021, James Zhan 詹波 (jfinal@126.com).
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

package com.jfinal.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import com.jfinal.config.Routes;
import com.jfinal.kit.StrKit;

/**
 * PathScanner 扫描 @Path 注解，实现路由扫描功能
 */
public class PathScanner {
	
	// 存放已被扫描过的 controller，避免被多次扫描
	private static final Set<Class<?>> scannedController = new HashSet<>();
	
	// 扫描的基础 package，只扫描该包及其子包之下的类
	private String basePackage;
	
	// 过滤不需要被扫描的类
	private Predicate<String> filter;
	
	// 调用 Routes.add(...) 添加扫描结果
	private Routes routes;
	
	private ClassLoader classLoader;
	
	public PathScanner(String basePackage, Routes routes, Predicate<String> filter) {
		if (StrKit.isBlank(basePackage)) {
			throw new IllegalArgumentException("basePackage can not be blank");
		}
		if (routes == null) {
			throw new IllegalArgumentException("routes can not be null");
		}
		
		String bp = basePackage.replace('.', '/');
		bp = bp.endsWith("/") ? bp : bp + '/';				// 添加后缀字符 '/'
		bp = bp.startsWith("/") ? bp.substring(1) : bp;		// 删除前缀字符 '/'
		
		this.basePackage = bp;
		this.routes = routes;
		this.filter = filter;
	}
	
	public PathScanner(String basePackage, Routes routes) {
		this(basePackage, routes, null);
	}
	
	public void scan() {
		try {
			classLoader = getClassLoader();
			List<URL> urlList = getResources();
			scanResources(urlList);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private ClassLoader getClassLoader() {
		ClassLoader ret = Thread.currentThread().getContextClassLoader();
		return ret != null ? ret : PathScanner.class.getClassLoader();
	}
	
	private List<URL> getResources() throws IOException {
		List<URL> ret = new ArrayList<>();
		
		// 用于去除重复
		Set<String> urlSet = new HashSet<>();
		// ClassLoader.getResources(...) 参数只支持包路径分隔符为 '/'，而不支持 '\'
		Enumeration<URL> urls = getClassLoader().getResources(basePackage);
		while (urls.hasMoreElements()) {
			URL url = urls.nextElement();
			if ( ! urlSet.contains(url.toString()) ) {
				urlSet.add(url.toString());
				ret.add(url);
			}
		}
		return ret;
	}
	
	private void scanResources(List<URL> urlList) throws IOException {
		for (URL url : urlList) {
			String protocol = url.getProtocol();
			if ("jar".equals(protocol)) {
				scanJar(url);
			} else if ("file".equals(protocol)) {
				scanFile(url);
			}
		}
	}
	
	private void scanJar(URL url) throws IOException {
		JarFile jarFile = null;
		try {
			URLConnection urlConn = url.openConnection();
			if (urlConn instanceof JarURLConnection) {
				JarURLConnection jarUrlConn = (JarURLConnection)urlConn;
				jarFile = jarUrlConn.getJarFile();
				
				Enumeration<JarEntry> jarFileEntries = jarFile.entries();
				while (jarFileEntries.hasMoreElements()) {
					JarEntry je = jarFileEntries.nextElement();
					String en = je.getName();
					// 只扫描 basePackage 之下的类
					if (en.endsWith(".class") && en.startsWith(basePackage)) {
						en = en.substring(0, en.length() - 6).replace(File.separatorChar, '.');
						scanController(en);
					}
				}
			}
		} finally {
			if (jarFile != null) {
				jarFile.close();
			}
		}
	}
	
	private void scanFile(URL url) {
		String path = url.getPath();
		path = decodeUrl(path);
		File file = new File(path);
		String classPath = getClassPath(file);
		scanFile(file, classPath);
	}
	
	private void scanFile(File file, String classPath) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) {
				for (File fi : files) {
					scanFile(fi, classPath);
				}
			}
		}
		else if (file.isFile()) {
			String fullName = file.getAbsolutePath();
			if (fullName != null && fullName.endsWith(".class")) {
				String className = fullName.substring(classPath.length(), fullName.length() - 6).replace(File.separatorChar, '.');
				scanController(className);
			}
		}
	}
	
	private String getClassPath(File file) {
		// 将 basePackage 中的路径分隔字符转换成与 OS 相同，方便处理路径
		String bp = basePackage.replace('/', File.separatorChar);
		
		String ret = file.getAbsolutePath();
		
		// 添加后缀，以便后续的 indexOf(bp) 可以正确获得下标值，因为 bp 确定有后缀
		if ( ! ret.endsWith(File.separator) ) {
			ret = ret + File.separator;
		}
		
		int index = ret.lastIndexOf(bp);
		if (index != -1) {
			ret = ret.substring(0, index);
		}
		
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	private void scanController(String className) {
		// 过滤不需要扫描的 className
		if (filter != null && filter.test(className)) {
			return ;
		}
		
		Class<?> c = loadClass(className);
		if (c != null && Controller.class.isAssignableFrom(c) && !scannedController.contains(c)) {
			// 确保 class 只被扫描一次
			scannedController.add(c);
			
			int mod = c.getModifiers();
			if (Modifier.isPublic(mod) && ! Modifier.isAbstract(mod)) {
				Path path = c.getAnnotation(Path.class);
				if (path != null) {
					String pa = path.value();
					String vp = path.viewPath();
					routes.add(pa, (Class<? extends Controller>)c, vp.length() != 0 ? vp : pa);
				}
			}
		}
	}
	
	private Class<?> loadClass(String className) {
		try {
			return classLoader.loadClass(className);
		} catch (NoClassDefFoundError | UnsupportedClassVersionError e) {
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return null;
	}
	
	/**
	 * 支持路径中存在空格百分号等等字符
	 */
	private String decodeUrl(String url) {
		try {
			return URLDecoder.decode(url, "UTF-8");
		} catch (java.io.UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}




