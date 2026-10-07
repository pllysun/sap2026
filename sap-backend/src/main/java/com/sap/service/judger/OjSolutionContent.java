package com.sap.service.judger;

import com.sap.common.BusinessException;
import com.sap.dto.judger.ProblemPack;
import com.sap.dto.judger.ProblemSolutionDocument;
import java.io.StringReader;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Enforces one teaching format and accepts only annotated validated programs. */
final class OjSolutionContent {
    static final List<String> KEYS=List.of("understand","approach","walkthrough","steps","correctness","complexity","pitfalls","review");
    static final List<String> TITLES=List.of("读懂题目","解法思路","样例推演","实现步骤","为什么正确","复杂度","边界与常见错误","复盘练习");
    static final Set<String> LANGUAGES=Set.of("c","cpp","java","python","rust");
    private static final Set<String> SVG_TAGS=Set.of("svg","g","rect","circle","ellipse","line","polyline","polygon","path","text","tspan","defs","marker","title","desc");
    private static final Set<String> SVG_ATTRS=Set.of("viewBox","width","height","x","y","x1","y1","x2","y2","cx","cy","r","rx","ry","points","d","fill","stroke","stroke-width","stroke-linecap","stroke-linejoin","stroke-dasharray","opacity","fill-opacity","stroke-opacity","font-size","font-family","font-weight","text-anchor","dominant-baseline","transform","marker-start","marker-end","marker-mid","id","xmlns","role","aria-label","dx","dy");
    private static void fail(String message){throw new BusinessException(400,message);}
    private static boolean text(String s,int max){return s!=null&&!s.isBlank()&&s.length()<=max;}
    static void validate(ProblemSolutionDocument d,ProblemPack pack){
        if(d==null||d.getSchemaVersion()!=1||!text(d.getSummary(),300)||d.getSections()==null||d.getSections().size()!=8)fail("题解须使用统一的八节结构");
        for(int i=0;i<8;i++){
            var s=d.getSections().get(i);
            if(s==null||!KEYS.get(i).equals(s.getKey())||!TITLES.get(i).equals(s.getTitle())||!text(s.getMarkdown(),18000))fail("题解章节格式不完整："+TITLES.get(i));
            if(s.getDiagram()!=null){if(!text(s.getDiagram().getCaption(),500))fail("图解必须有文字说明");validateSvg(s.getDiagram().getSvg());}
        }
        if(d.getCodes()==null||!d.getCodes().keySet().equals(new HashSet<>(pack.getModes())))fail("题解代码模式必须与题目一致");
        for(String mode:pack.getModes()){
            var codes=d.getCodes().get(mode);if(codes==null||!codes.keySet().equals(LANGUAGES))fail("每种做题模式必须包含五语言代码");
            for(String language:LANGUAGES){
                String code=codes.get(language),reference=pack.getReferences().get(language).get(mode);
                if(!text(code,60000))fail("题解代码缺失或过长："+language);
                if(!equivalent(reference,code,language))fail("题解代码须保持已验证参考实现的逻辑："+language+" / "+mode);
                if(!code.contains("python".equals(language)?"#":"//")&&!code.contains("/*"))fail("题解代码必须包含说明注释："+language);
            }
        }
    }
    static void validateSvg(String svg){
        if(!text(svg,100000)||svg.contains("<!")||svg.contains("<?"))fail("图解格式不安全或过大");
        try{
            var factory=DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            var builder=factory.newDocumentBuilder();builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){
                @Override public void error(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}
                @Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}
            });
            Element root=builder.parse(new InputSource(new StringReader(svg))).getDocumentElement();
            if(!"svg".equals(root.getTagName())||!root.hasAttribute("viewBox"))fail("图解须为可缩放 SVG");
            var queue=new ArrayDeque<Element>();queue.add(root);int count=0;
            while(!queue.isEmpty()){
                Element element=queue.remove();if(++count>600||!SVG_TAGS.contains(element.getTagName()))fail("图解包含不允许的元素");
                NamedNodeMap attrs=element.getAttributes();
                for(int j=0;j<attrs.getLength();j++){
                    Node a=attrs.item(j);String name=a.getNodeName(),value=a.getNodeValue(),lower=value.toLowerCase(Locale.ROOT);
                    if(!SVG_ATTRS.contains(name))fail("图解包含不允许的属性");
                    if("xmlns".equals(name)){if(!"http://www.w3.org/2000/svg".equals(value))fail("SVG 命名空间无效");}
                    else if(lower.contains("url(")||lower.contains("javascript:")||lower.contains("data:")||lower.contains("://")||value.length()>12000)fail("图解不得包含外部资源");
                }
                NodeList children=element.getChildNodes();for(int j=0;j<children.getLength();j++)if(children.item(j) instanceof Element child)queue.add(child);
            }
        }catch(BusinessException e){throw e;}catch(Exception e){fail("图解须为有效、安全的 SVG");}
    }
    /** Token equality ignores comments/formatting but preserves literal contents and Python indentation. */
    static boolean equivalent(String reference,String annotated,String language){
        return reference!=null&&annotated!=null&&tokens(reference,language).equals(tokens(annotated,language));
    }
    private static List<String> tokens(String source,String language){
        boolean python="python".equals(language);var out=new ArrayList<String>();int i=0,brackets=0;boolean lineStart=python;
        while(i<source.length()){
            char c=source.charAt(i);
            if(python&&lineStart){int start=i;while(i<source.length()&&(source.charAt(i)==' '||source.charAt(i)=='\t'))i++;
                if(i>=source.length())break;c=source.charAt(i);
                if(brackets==0&&c!='#'&&c!='\r'&&c!='\n')out.add("INDENT:"+source.substring(start,i));lineStart=false;
            }
            // A wrapped expression inside (), [] or {} has no Python suite indentation.
            if(c=='\n'){if(python){if(brackets==0)out.add("NL");lineStart=true;}i++;continue;}
            if(Character.isWhitespace(c)){i++;continue;}
            if((python&&c=='#')||(!python&&source.startsWith("//",i))){while(i<source.length()&&source.charAt(i)!='\n')i++;
                // Whole-line Python comments carry no indentation or newline token.
                if(python&&(out.isEmpty()||"NL".equals(out.getLast()))){if(i<source.length())i++;lineStart=true;}continue;}
            if(!python&&source.startsWith("/*",i)){int end=source.indexOf("*/",i+2);if(end<0)return List.of("INVALID_COMMENT",source);i=end+2;continue;}
            boolean quoted=c=='"'||c=='\''&&(python||!"rust".equals(language)||isRustChar(source,i));
            if(quoted){int start=i;String delimiter=String.valueOf(c);if(python&&source.startsWith(delimiter.repeat(3),i))delimiter=delimiter.repeat(3);
                i+=delimiter.length();while(i<source.length()){if(source.charAt(i)=='\\'){i=Math.min(source.length(),i+2);continue;}
                    if(source.startsWith(delimiter,i)){i+=delimiter.length();break;}i++;}
                out.add(source.substring(start,i));continue;}
            if(Character.isJavaIdentifierStart(c)||Character.isDigit(c)){
                int start=i++;while(i<source.length()&&(Character.isJavaIdentifierPart(source.charAt(i))||Character.isDigit(source.charAt(i))))i++;out.add(source.substring(start,i));continue;}
            String operator=null;for(String op:List.of("<<=",">>=","...","::","->","=>","++","--","+=","-=","*=","/=","%=","==","!=","<=",">=","&&","||","<<",">>","**","//",":=",".."))if(source.startsWith(op,i)){operator=op;break;}
            if(operator!=null){out.add(operator);i+=operator.length();}else{if(python){if("([{ ".stripTrailing().indexOf(c)>=0)brackets++;else if(")]}".indexOf(c)>=0)brackets--;}out.add(String.valueOf(c));i++;}
        }
        // Blank lines and whole-line comments do not alter Python suites.
        if(python){var normalized=new ArrayList<String>();for(String token:out)if(!"NL".equals(token)||!normalized.isEmpty()&&!"NL".equals(normalized.getLast()))normalized.add(token);
            while(!normalized.isEmpty()&&"NL".equals(normalized.getLast()))normalized.removeLast();return normalized;}
        return out;
    }
    private static boolean isRustChar(String s,int i){
        if(i+2<s.length()&&s.charAt(i+2)=='\'')return true;
        if(i+3<s.length()&&s.charAt(i+1)=='\\')return s.indexOf('\'',i+2)>=0&&s.indexOf('\'',i+2)-i<=10;
        return false;
    }
}
