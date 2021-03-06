package org.angularjs.codeInsight.refs;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.javascript.psi.*;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SoftFileReferenceSet;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import org.angularjs.index.AngularIndexUtil;
import org.angularjs.index.AngularJSIndexingHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class AngularJSReferencesContributor extends PsiReferenceContributor {
  private static final PsiElementPattern.Capture<JSLiteralExpression> TEMPLATE_PATTERN = literalInProperty("templateUrl");
  private static final PsiElementPattern.Capture<JSLiteralExpression> CONTROLLER_PATTERN = literalInProperty("controller");
  private static final PsiElementPattern.Capture<JSLiteralExpression> NG_INCLUDE_PATTERN =
    PlatformPatterns.psiElement(JSLiteralExpression.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (element instanceof JSLiteralExpression) {
          final JSLiteralExpression literal = (JSLiteralExpression)element;
          if (literal.isQuotedLiteral()) {
            final PsiElement original = CompletionUtil.getOriginalOrSelf(literal);
            final PsiLanguageInjectionHost host = InjectedLanguageUtil.findInjectionHost(original);
            if (host instanceof XmlAttributeValue) {
              final PsiElement parent = host.getParent();
              return parent instanceof XmlAttribute && "ng-include".equals(((XmlAttribute)parent).getName());
            }
          }
        }
        return false;
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }));
  private static final PsiElementPattern.Capture<JSLiteralExpression> STYLE_PATTERN = PlatformPatterns.psiElement(JSLiteralExpression.class).and(new FilterPattern(new ElementFilter() {
    @Override
    public boolean isAcceptable(Object element, @Nullable PsiElement context) {
      if (element instanceof JSLiteralExpression) {
        final JSLiteralExpression literal = (JSLiteralExpression)element;
        if (literal.isQuotedLiteral()) {
          if ((literal.getParent() instanceof JSArrayLiteralExpression)) {
            final JSProperty property = ObjectUtils.tryCast(literal.getParent().getParent(), JSProperty.class);
            if (property != null && "styleUrls".equals((property).getName())) {
              return AngularIndexUtil.hasAngularJS(literal.getProject());
            }
          }
        }
      }
      return false;
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }));

  public static final PsiElementPattern.Capture<JSParameter> DI_PATTERN = PlatformPatterns.psiElement(JSParameter.class).and(new FilterPattern(new ElementFilter() {
    @Override
    public boolean isAcceptable(Object element, @Nullable PsiElement context) {
      return AngularJSIndexingHandler.isInjectable(context);
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }));

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    final AngularJSTemplateReferencesProvider templateProvider = new AngularJSTemplateReferencesProvider();
    registrar.registerReferenceProvider(TEMPLATE_PATTERN, templateProvider);
    registrar.registerReferenceProvider(NG_INCLUDE_PATTERN, templateProvider);
    registrar.registerReferenceProvider(STYLE_PATTERN, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return new SoftFileReferenceSet(element).getAllReferences();
      }
    });
    registrar.registerReferenceProvider(CONTROLLER_PATTERN, new AngularJSControllerReferencesProvider());
    registrar.registerReferenceProvider(DI_PATTERN, new AngularJSDIReferencesProvider());
  }

  private static PsiElementPattern.Capture<JSLiteralExpression> literalInProperty(final String propertyName) {
    return PlatformPatterns.psiElement(JSLiteralExpression.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (element instanceof JSLiteralExpression) {
          final JSLiteralExpression literal = (JSLiteralExpression)element;
          if (literal.isQuotedLiteral()) {
            final PsiElement parent = literal.getParent();
            if (parent instanceof JSProperty && propertyName.equals(((JSProperty)parent).getName())) {
              return AngularIndexUtil.hasAngularJS(literal.getProject());
            }
          }
        }
        return false;
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }));
  }
}
