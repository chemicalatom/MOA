package kr.co.moa.keyword.anlyzer.morpheme;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import kr.co.DebuggingLog;
import kr.co.data.EventData;
import kr.co.data.EventParsedData;
import kr.co.data.HtmlData;
import kr.co.data.HtmlParsedData;
import kr.co.moa.DBManager;

import org.bitbucket.eunjeon.seunjeon.Analyzer;
import org.bitbucket.eunjeon.seunjeon.LNode;

import com.google.gson.Gson;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;

public class MorphemeAnalyzer {

	/*2015-12-30
	 * 1. Remove noise
	 * 2. 형태소 분석 by 메캅
	 * 3. indexing to DB
	 * 
	 * Author by dongyoung  
	 */
	private static MorphemeAnalyzer instance;
	private Map<String,String> TagsMap;
	private Map<String,String> TexttagMap;
	private String userid;
	private String url;
	
	
	 private static final String[] uselessTags = {
	            "script", 	"noscript", "style", 	"meta", 	"link",
	            "noframes", "nav", 		"aside", 	"hgroup", 	"header", 
	            "footer", 	"math",		"button", 	"fieldset", "input", 
	            "keygen", 	"object", 	"output", 	"select", 	"textarea",
	            "img", 		"br", 		"wbr", 		"embed", 	"hr",
	            "col", 		"colgroup", "command",	"device", 	"area", 
	            "basefont", "bgsound", 	"menuitem", "param", 	"track",
	            "a",		"i",		"aside"
	 };
	 //표는 버린다.ㅋ
	 private static final String[] textTags = {
			 "title", 	"p", 		"h1", 			"h2", 		"h3", 
			 "h4", 		"h5", 		"h6", 			"pre", 		"address",
	         "ins", 	"textarea",	"blockquote", 	"dt",		"dd",
	         "span",	"b",		"font",			"strong"
	 };
	
	public static MorphemeAnalyzer getInstance(){
		if(instance == null) 	instance = new MorphemeAnalyzer();
		return 					instance;
	}
	
	private MorphemeAnalyzer(){
		TagsMap = new HashMap<String,String>();
		for(String tag : uselessTags){	TagsMap.put(tag, null);		}
		TexttagMap = new HashMap<String,String>();
		for(String tag : textTags)   {	TexttagMap.put(tag, null);	}
	}
	
	public void parsingHTML(HtmlData html){
		url = html.url;
		userid = html.userid;
		//DebuggingLog debug = new DebuggingLog("Content");
		HtmlParser hp = new HtmlParser();
		HtmlParsedData hpd = new HtmlParsedData(html.userid, html.url, html.time);
		/*
		 *  public String userid = html.userid;
			public String url = html.url;
			public String time = html.time;
		
			public String title = makeCBT()에서 처리;
			public String imgsrc = makeCBT()에서 처리;
			public Map<String,String> keywordList= domecab();
		 */
		String content = hp.makeCBT(html, TagsMap, TexttagMap, hpd).makeTopicTree();
		if(content.equals("") || content.trim().length() <120){
			System.out.println("lamda decrease");
	        hp = new HtmlParser();
	        hp.lamda = 0.05;
	        content = hp.makeCBT(html, TagsMap, TexttagMap, hpd).makeTopicTree();
	    }else
	        System.out.println("length :" + content.length());
	      
//		debug.write(content);
//		debug.writeln();
//	  	debug.writeln();
	  	System.out.println("title : "  		+ hpd.title );
	  	System.out.println("content : "		+ content   );
	  	System.out.println("decription : "	+ hpd.imrsrc);
//	    Map words_map = doMecab(content, "html");
	  	Map words_map = doMecabProcess(content, "html");
	  	hpd.keywordList = words_map;
	     
	  	//debug.close();
	  	try {
	  		DBManager.getInstnace().insertData("ParsedHtmlCollection", new Gson().toJson(hpd));
	  	} catch (Exception e) {
	  		e.printStackTrace();
	  	}
	}
	
	public void parsingEvent(EventData eventData){
		url = eventData.url;
		userid = eventData.userid;
		String content = eventData.data;
		System.out.println(content);
		
		
		//debug.write(content);
		//debug.writeln();
		//debug.writeln();
		
//		Map words_map = doMecab(content, "event");
		Map words_map = doMecabProcess(content, "event");
		EventParsedData epd = new EventParsedData(  eventData.userid, 
													eventData.url, 
												    eventData.time, 
												    words_map);
		
		try {
			DBManager.getInstnace().insertData("ParsedEventCollection", new Gson().toJson(epd));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map doMecab(String content, String kind){
//		DebuggingLog debug;
//		if(kind.equals("event"))
//			 debug = new DebuggingLog("KeywordsEvent");
//		else
//			 debug = new DebuggingLog("KeywordsHTml");
		List<LNode> result = Analyzer.parseJava(content);
		String word, type;
		Map<String, Integer> countingMap = new HashMap<String, Integer>();
		ValueComparator bvc = new ValueComparator(countingMap);
        TreeMap sorted_map = new TreeMap(bvc);
        
        //Stanford POS tagger
        MaxentTagger tagger = null;
	    try {
	    	tagger = new MaxentTagger("C:\\Users\\dong\\Documents\\moa_gitt\\MOA\\left3words-wsj-0-18.tagger");
	    } catch (ClassNotFoundException e1) {
	    	e1.printStackTrace();
	    } catch (IOException e1) {
	    	e1.printStackTrace();
	    }
	    
	    //System.out.println(tagger.tagString(content));
        
        for (LNode term: result) {
			type = term.morpheme().feature().head();
			
			if(type.charAt(0) != 'N'   && 			//ignore NOT a noun
					!type.equals("SL") &&			//ignore NOT a foreign language
					!type.equals("SN") ) continue;	//ignore NOT a number
			if(type.equals("SL")){					//if foreign laguage do Stanford POS tagger
				word = tagger.tagString(term.morpheme().surface());
				String[] temp = word.split("/");
				if(temp[1].charAt(0) != 'N') 	continue;	//명사가 아닌 영어 무시  
			}
			word = term.morpheme().surface();
			//System.out.println(type + " : " + word +"\n");
			
			if(countingMap.containsKey(word)) 	countingMap.put(word, countingMap.get(word) + 1);
			else 								countingMap.put(word, 1);
		}

		sorted_map.putAll(countingMap);
		Collection<String>  keys 	= sorted_map.keySet();
		Collection<Integer> values	= sorted_map.values();
		Iterator key_iter = keys.iterator();
		Iterator val_iter = values.iterator();
		int count = 10;
		System.out.println("key\t count\t");
		//debug.write("key\t count\t");
		while(key_iter.hasNext()){//count-- > 0){
			String ikey = (String)  key_iter.next();
			int ival 	= (Integer) val_iter.next();
			System.out.println(ikey + "\t " + ival);
			//debug.write(ikey + "\t " + ival);
			//debug.writeln();
		}
		System.out.println("done");
		// return 저장할 Json 형태
		// Event 경우 
		/*
		"url" : "http://yeop9657.blog.me/220374891289",
		"keword" : cnt,
		"keword" : cnt,
		"type" : "scroll",
		 등등 등
		
		*/
		//debug.close();
		return sorted_map;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map doMecabProcess(String content, String kind){
		DebuggingLog debug;
//		if(kind.equals("event"))
//			 debug = new DebuggingLog("KeywordsEvent");
//		else
//			 debug = new DebuggingLog("KeywordsHTml");
//		
		UUID uuid = UUID.randomUUID();
		String inputPath  = "D:/mecab/" + userid + "/";
		String outputPath = "D:/mecab/" + userid + "/";
		String inputFile  = uuid+".txt";
		String outputFile = uuid+"_out.txt";
		
//		String inputPath  = "/librarys/mecab-ko/";
//		String outputPath = "/librarys/mecab-ko/";
//		String inputFile  = "test.txt";
//		String outputFile = "test_out.txt";
		
		System.out.println(uuid);
		
		if(!makeInputFile(inputPath + inputFile, content)){
			//inputfile 생성 오류처리
		}
		
		List<String> arg = new ArrayList<String>();
		arg.add("D:/mecab/mecab-ko/mecab");
		arg.add("-d");
		arg.add("D:/mecab/mecab-ko/dic/mecab-ko-dic");
		arg.add(inputPath + inputFile);
		arg.add("-o");
		arg.add(outputPath + outputFile);
		
		try {
			System.out.println("start process------------------------");
			ProcessBuilder mecab_builder = new ProcessBuilder(arg);
			mecab_builder.redirectOutput(Redirect.INHERIT);	//에러와 출력을 표준스트림으로 출력시킴
			mecab_builder.redirectError(Redirect.INHERIT);	//input-buffer overflow. The line is split. use -b #SIZE option.
			Process mecab_process = mecab_builder.start();
			mecab_process.waitFor();
			System.out.println("end process--------------------------");
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//Stanford POS tagger
        MaxentTagger tagger = null;
	    try {
	    	tagger = new MaxentTagger("C:\\Users\\dong\\Documents\\moa_gitt\\MOA\\left3words-wsj-0-18.tagger");
	    } catch (ClassNotFoundException e1) {
	    	e1.printStackTrace();
	    } catch (IOException e1) {
	    	e1.printStackTrace();
	    }
	    
	    String word, type;
		
        Map<String, Integer> countingMap = new HashMap<String, Integer>();
		ValueComparator bvc = new ValueComparator(countingMap);
        TreeMap sorted_map = new TreeMap(bvc);
        
	    //System.out.println(tagger.tagString(content));
        
        List<WordTagPair> result = getVaildTag(outputPath + outputFile);
        if(result == null){
        	//mecab오류 예외처리
        }
        
        for (WordTagPair term: result) {
			type = term.tag;
			
			if(type.equals("SL")){					//if foreign laguage do Stanford POS tagger
				word = tagger.tagString(term.word);
				//System.out.println(word);
				String[] temp = word.split("/");
				if(temp[1].charAt(0) != 'N') 	continue;	//명사가 아닌 영어 무시  
			}
			word = term.word;
			
			if(countingMap.containsKey(word)) 	countingMap.put(word, countingMap.get(word) + 1);
			else 								countingMap.put(word, 1);
		}

		sorted_map.putAll(countingMap);
		Collection<String>  keys 	= sorted_map.keySet();
		Collection<Integer> values	= sorted_map.values();
		Iterator key_iter = keys.iterator();
		Iterator val_iter = values.iterator();
		int count = 10;
		System.out.println("key\t count\t");
		//debug.write("key\t count\t");
		while(key_iter.hasNext()){//count-- > 0){
			String ikey = (String)  key_iter.next();
			int ival 	= (Integer) val_iter.next();
			System.out.println(ikey + "\t " + ival);
			//debug.write(ikey + "\t " + ival);
			//debug.writeln();
		}
		System.out.println("done");
		// return 저장할 Json 형태
		// Event 경우 
		/*
		"url" : "http://yeop9657.blog.me/220374891289",
		"keword" : cnt,
		"keword" : cnt,
		"type" : "scroll",
		 등등 등
		
		*/
		//debug.close();
		return sorted_map;
	}
	
	class ValueComparator implements Comparator {
	    Map<String, Integer> base;

	    public ValueComparator(Map base) {
	        this.base = base;
	    }

		@Override
		public int compare(Object o1, Object o2) {
			if (base.get(o1) >= base.get(o2)) {
	            return -1;
	        } else {
	            return 1;
	        } // returning 0 would merge keys
		}
	}

	class WordTagPair {
		String word;
		String tag;
	}
	
	private List getVaildTag (String filepath){
		BufferedReader in;
		ArrayList<WordTagPair> result = new ArrayList<WordTagPair>();
		try {
			in = new BufferedReader(new FileReader(filepath));
			String str;

		    while ((str = in.readLine()) != null) {
		    	if(str.length() == 3 && str.equals("EOS")) continue;
		    	String[] tok = str.split("\t");
		    	if(!tok[1].substring(0, 3).equals("NNG") 		&& 		//ignore NOT a noun
		    			!tok[1].substring(0, 3).equals("NNP") 	&&		//ignore NOT a foreign language
		    			!tok[1].substring(0, 2).equals("SL") 	&&		//ignore NOT a foreign language
		    			!tok[1].substring(0, 2).equals("SN")) continue;	//ignore NOT a number
		    	WordTagPair entry = new WordTagPair();
		    	entry.word = tok[0];
		    	entry.tag = tok[1].split(",")[0];
		    	result.add(entry);
		    	
		    }
		    in.close();
		    
		    //for(WordTagPair p : result){
	        //	System.out.println(p.word + "/" + p.tag);
	        //}
		    return result;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}	    		
		return null;
	}
	
	private boolean makeInputFile(String path, String content){
		String dirpath = "/mecab/" + userid;
		dirpath.replace("/", "\\\\");
		dirpath = "D:" + dirpath;
		File dir = new File(dirpath);
		if( !dir.exists() ){
			dir.mkdir();
		}
					
		path.replace("/", "\\\\");
		//path = "D:" + path;
		BufferedWriter out;
		try {
			out = new BufferedWriter(new FileWriter(path));
		    out.write(content);
		    out.close();
		    return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
	    return false;
	}
}


