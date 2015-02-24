/*
 * Copyright 2015 The authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


// Generated from ognl.bnf, do not modify
package com.intellij.lang.ognl.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ognl.psi.OgnlFqnTypeExpression;
import com.intellij.lang.ognl.psi.OgnlVisitor;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class OgnlFqnTypeExpressionImpl extends OgnlFqnTypeExpressionBase implements OgnlFqnTypeExpression {

  public OgnlFqnTypeExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof OgnlVisitor) ((OgnlVisitor)visitor).visitFqnTypeExpression(this);
    else super.accept(visitor);
  }

}