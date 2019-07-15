package org.eclipse.scout.migration.ecma6.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.eclipse.scout.migration.ecma6.FileUtility;
import org.eclipse.scout.migration.ecma6.WorkingCopy;
import org.eclipse.scout.migration.ecma6.context.Context;
import org.eclipse.scout.migration.ecma6.task.json.IConstPlaceholderMapper;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.util.Assertions;
import org.eclipse.scout.rt.platform.util.StringUtility;

public class T30000_JsonToJsModule extends AbstractTask {

  private static final Pattern KEY_PAT = Pattern.compile("\"(\\w+)\":");
  private static final Pattern TRAILING_WHITESPACE_CHARS_PAT = Pattern.compile("\\s$");
  private static final String JSON_EXTENSION = "json";
  private static final String JS_EXTENSION = "js";
  private static final String ESCAPED_REPLACEMENT = "@@@_@@@escaped@@@_@@@";
  public static final String JSON_MODEL_NAME_SUFFIX = "Model";
  public static final String SCOUT_IMPORT = "scout";
  public static final String MODEL_OWNER_PARAM_NAME = "modelOwner";

  private static final Pattern PLACEHOLDER_PAT = Pattern.compile("(\\w+):\\s*'\\$\\{(\\w+):([^}]+)}'");

  private List<IConstPlaceholderMapper> m_placeholderMappers;

  @Override
  public boolean accept(Path file, Path moduleRelativeFile, Context context) {
    if (!FileUtility.hasExtension(file, JSON_EXTENSION)) {
      return false;
    }

    WorkingCopy candidate = context.ensureWorkingCopy(file);
    return candidate.getSource().contains("\"objectType\":");
  }

  @PostConstruct
  private void init() {
    m_placeholderMappers = BEANS.all(IConstPlaceholderMapper.class);
  }

  @Override
  public void process(Path file, Context context) {
    WorkingCopy workingCopy = context.ensureWorkingCopy(file);
    String originalSource = workingCopy.getSource();

    // migrate scout json model file to js file
    String step1 = KEY_PAT.matcher(originalSource).replaceAll("$1:");
    String step2 = step1.replace("\\\"", ESCAPED_REPLACEMENT);
    String step3 = step2.replace('"', '\'');
    String step4 = step3.replace(ESCAPED_REPLACEMENT, "\\'");
    String step5 = TRAILING_WHITESPACE_CHARS_PAT.matcher(step4).replaceAll("");
    String step6 = "export default function(" + MODEL_OWNER_PARAM_NAME + ") {\n" +
        "  return " + step5 + ";\n}\n";

    String step7 = migratePlaceholders(step6, file, context);

    // change file name from Xyz.json to XyzModel.js
    Path jsonRelPath = context.getSourceRootDirectory().relativize(file);
    String jsonFileName = jsonRelPath.getFileName().toString();
    String jsFileName = jsonFileName.substring(0, jsonFileName.length() - JSON_EXTENSION.length() - 1) + JSON_MODEL_NAME_SUFFIX + '.' + JS_EXTENSION;
    Path newRelPath = jsonRelPath.getParent().resolve(jsFileName);
    Path newFileNameInSourceFolder = file.getParent().resolve(jsFileName);
    Assertions.assertFalse(Files.exists(newFileNameInSourceFolder),
        "The migration of file '{}' would be stored in '{}' but this file already exists in the source folder!", file, newRelPath);

    workingCopy.setSource(step7);
    workingCopy.setRelativeTargetPath(newRelPath);
  }

  protected String migratePlaceholders(String newSource, Path file, Context context) {
    Matcher matcher = PLACEHOLDER_PAT.matcher(newSource);
    StringBuilder result = new StringBuilder(newSource.length() * 2);
    int lastPos = 0;
    Set<String> importsToAdd = new HashSet<>();
    while (matcher.find()) {
      result.append(newSource, lastPos, matcher.start());
      String key = matcher.group(1);
      String type = matcher.group(2);
      String value = matcher.group(3);
      result.append(key).append(": ");
      if ("textKey".equals(type)) {
        result.append(migratePlaceholderTextKey(value, file, importsToAdd));
      }
      else if ("const".equals(type)) {
        result.append(migratePlaceholderConst(value, key, file, context, importsToAdd));
      }
      else if ("iconId".equals(type)) {
        result.append(migratePlaceholderIconId(value, file, importsToAdd));
      }
      else {
        Assertions.fail("unknown json placeholder: '{}' in file '{}'.", type, file);
      }
      lastPos = matcher.end();
    }
    result.append(newSource, lastPos, newSource.length());

    result.insert(0, getImports(importsToAdd));
    return result.toString();
  }

  protected String getImports(Collection<String> imports) {
    if (imports.isEmpty()) {
      return "";
    }

    boolean hasScoutRef = imports.remove(SCOUT_IMPORT);
    StringBuilder sb = new StringBuilder("import ");
    if (hasScoutRef) {
      sb.append(SCOUT_IMPORT);
      if (!imports.isEmpty()) {
        sb.append(',');
      }
      sb.append(' ');
    }
    if (!imports.isEmpty()) {
      sb.append("{ ").append(String.join(", ", imports)).append(" } ");
    }
    sb.append("from '@eclipse-scout/eclipse-scout';\n\n");
    return sb.toString();
  }

  protected String migratePlaceholderTextKey(String key, Path file, Set<String> importsToAdd) {
    Assertions.assertTrue(StringUtility.hasText(key), "Empty textKey placeholder in json model '{}'.", file);
    // TODO: improve nls lookup?
    importsToAdd.add(SCOUT_IMPORT);
    return "scout.texts.resolveText('" + key + "', " + MODEL_OWNER_PARAM_NAME + ".session.locale.languageTag)";
  }

  protected String migratePlaceholderIconId(String iconId, Path file, Set<String> importsToAdd) {
    Assertions.assertTrue(StringUtility.hasText(iconId), "Empty iconId placeholder in json model '{}'.", file);

    int lastDotPos = iconId.lastIndexOf('.');
    if (lastDotPos > 0) {
      // qualified
      return iconId.substring(0, lastDotPos) + ".icons" + iconId.substring(lastDotPos);
    }

    importsToAdd.add(SCOUT_IMPORT);
    return "scout.icons." + iconId;
  }

  protected String migratePlaceholderConst(String constValue, String key, Path file, Context context, Set<String> importsToAdd) {
    Assertions.assertTrue(StringUtility.hasText(key), "Empty key in json model '{}'.", key, file);
    Assertions.assertTrue(StringUtility.hasText(constValue), "Empty const placeholder for attribute '{}' in json model '{}'.", key, file);
    for (IConstPlaceholderMapper mapper : m_placeholderMappers) {
      String migrated = mapper.migrate(key, constValue, file, context, importsToAdd);
      if (migrated != null) {
        return migrated;
      }
    }
    importsToAdd.add(SCOUT_IMPORT);
    return "scout.objects.resolveConst('" + constValue + "')"; // default migration
  }
}