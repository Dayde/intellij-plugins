package com.intellij.javascript.flex.css;

import com.intellij.javascript.flex.FlexAnnotationNames;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.lang.javascript.index.JSPackageIndex;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.ecmal4.JSAttribute;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeNameValuePair;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.lang.javascript.psi.stubs.JSClassStub;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class FlexStyleIndex extends FileBasedIndexExtension<String, Set<FlexStyleIndexInfo>> {

  public static final ID<String, Set<FlexStyleIndexInfo>> INDEX_ID = ID.create("js.style.index");

  private static final int VERSION = 18;

  private final KeyDescriptor<String> myKeyDescriptor = new EnumeratorStringDescriptor();

  private final DataExternalizer<Set<FlexStyleIndexInfo>> myDataExternalizer = new DataExternalizer<Set<FlexStyleIndexInfo>>() {

    @Override
    public void save(@NotNull DataOutput out, Set<FlexStyleIndexInfo> value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.size());
      for (FlexStyleIndexInfo info : value) {
        writeUTF(out, info.getClassOrFileName());
        writeUTF(out, info.getAttributeName());
        writeUTF(out, info.getInherit());
        writeUTF(out, info.getType());
        writeUTF(out, info.getArrayType());
        writeUTF(out, info.getFormat());
        writeUTF(out, info.getEnumeration());
        out.writeBoolean(info.isInClass());
      }
    }

    @Override
    public Set<FlexStyleIndexInfo> read(@NotNull DataInput in) throws IOException {
      int size = DataInputOutputUtil.readINT(in);
      Set<FlexStyleIndexInfo> result = ContainerUtil.newLinkedHashSet();
      for (int i = 0; i < size; i++) {
        String className = readUTF(in);
        assert className != null;
        String attributeName = readUTF(in);
        assert attributeName != null;
        String inherit = readUTF(in);
        assert inherit != null;
        String type = readUTF(in);
        String arrayType = readUTF(in);
        String format = readUTF(in);
        String enumeration = readUTF(in);
        boolean inClass = in.readBoolean();
        result.add(new FlexStyleIndexInfo(className, attributeName, inherit, type, arrayType, format, enumeration, inClass));
      }
      return result;
    }
  };

  @NotNull
  @Override
  public ID<String, Set<FlexStyleIndexInfo>> getName() {
    return INDEX_ID;
  }

  @Nullable
  private static String readUTF(@NotNull DataInput in) throws IOException {
    String s = IOUtil.readUTF(in);
    return s.length() == 0 ? null : s;
  }

  private static void writeUTF(@NotNull DataOutput out, @Nullable String s) throws IOException {
    IOUtil.writeUTF(out, s != null ? s : "");
  }

  private static <TKey, TValue> void addElement(Map<TKey, Set<TValue>> map, TKey key, TValue value) {
    Set<TValue> list = map.get(key);
    if (list == null) {
      list = ContainerUtil.newLinkedHashSet();
      map.put(key, list);
    }
    list.add(value);
  }

  @NotNull
  private static String getQualifiedNameByMxmlFile(@NotNull VirtualFile file, @NotNull Project project) {
    String name = FileUtil.getNameWithoutExtension(file.getName());
    final String packageName = JSResolveUtil.getExpectedPackageNameFromFile(file, project);
    if (packageName != null && packageName.length() > 0) {
      return packageName + "." + name;
    }
    return name;
  }

  private static void indexMxmlFile(@NotNull final XmlFile file,
                                    @NotNull final VirtualFile virtualFile,
                                    @NotNull final Map<String, Set<FlexStyleIndexInfo>> map) {
    XmlTag rootTag = getRootTag(file);
    if (rootTag != null) {
      final String classQName = getQualifiedNameByMxmlFile(virtualFile, file.getProject());
      final JSResolveUtil.JSInjectedFilesVisitor jsFilesVisitor = new JSResolveUtil.JSInjectedFilesVisitor() {
        @Override
        protected void process(JSFile file) {
          indexAttributes(file, classQName, true, map);
        }
      };
      FlexUtils.processMxmlTags(rootTag, false, jsFilesVisitor);
    }
  }

  @Nullable
  private static XmlTag getRootTag(XmlFile file) {
    XmlDocument document = file.getDocument();
    if (document != null) {
      return document.getRootTag();
    }
    return null;
  }

  @NotNull
  @Override
  public DataIndexer<String, Set<FlexStyleIndexInfo>, FileContent> getIndexer() {
    return new DataIndexer<String, Set<FlexStyleIndexInfo>, FileContent>() {
      @Override
      @NotNull
      public Map<String, Set<FlexStyleIndexInfo>> map(@NotNull FileContent inputData) {
        final THashMap<String, Set<FlexStyleIndexInfo>> map = new THashMap<String, Set<FlexStyleIndexInfo>>();
        if (JavaScriptSupportLoader.isFlexMxmFile(inputData.getFileName())) {
          PsiFile file = inputData.getPsiFile();
          VirtualFile virtualFile = inputData.getFile();
          if (file instanceof XmlFile) {
            indexMxmlFile((XmlFile)file, virtualFile, map);
          }
        }
        else {
          StubTree tree = JSPackageIndex.getStubTree(inputData);
          if (tree != null) {
            for (StubElement e : tree.getPlainList()) {
              if (e instanceof JSClassStub) {
                final PsiElement psiElement = e.getPsi();
                if (psiElement instanceof JSClass) {
                  final String qName = ((JSClass)psiElement).getQualifiedName();
                  indexAttributes(psiElement, qName, true, map);
                }
              }
              else if (e instanceof PsiFileStub) {
                PsiElement psiElement = e.getPsi();
                if (psiElement instanceof JSFile) {
                  String name = ((JSFile)psiElement).getName();
                  indexAttributes(psiElement, name, false, map);
                }
              }
            }
          }
        }
        return map;
      }
    };
  }

  private static void indexAttributes(PsiElement element, final String classQName, final boolean inClass, final Map<String, Set<FlexStyleIndexInfo>> map) {
    JSResolveUtil.processMetaAttributesForClass(element, new JSResolveUtil.MetaDataProcessor() {
      @Override
      public boolean process(@NotNull JSAttribute jsAttribute) {
        String attrName = jsAttribute.getName();
        if (attrName != null && FlexAnnotationNames.STYLE.equals(attrName)) {
          JSAttributeNameValuePair pair = jsAttribute.getValueByName("name");
          String propertyName = pair != null ? pair.getSimpleValue() : null;
          if (propertyName != null) {
            if (classQName != null) {
              FlexStyleIndexInfo info = FlexStyleIndexInfo.create(classQName, propertyName, jsAttribute, inClass);
              if (info != null) {
                addElement(map, propertyName, info);
                String classicPropertyName = FlexCssUtil.toClassicForm(propertyName);
                if (!propertyName.equals(classicPropertyName)) {
                  addElement(map, classicPropertyName, info);
                }
              }
            }
          }
        }
        return true;
      }

      @Override
      public boolean handleOtherElement(PsiElement el, PsiElement context, @Nullable Ref<PsiElement> continuePassElement) {
        return true;
      }
    }, false);
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @NotNull
  @Override
  public DataExternalizer<Set<FlexStyleIndexInfo>> getValueExternalizer() {
    return myDataExternalizer;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return FlexInputFilter.getInstance();
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

}
