/*
 * Copyright (c) 2014-2017 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
import {keys} from '../../index';
import {AbstractTreeNavigationKeyStroke} from '../../index';

export default class TreeNavigationDownKeyStroke extends AbstractTreeNavigationKeyStroke {

  constructor(tree, modifierBitMask) {
    super(tree, modifierBitMask);
    this.which = [keys.DOWN];
    this.renderingHints.text = '↓';
    this.renderingHints.$drawingArea = function($drawingArea, event) {
      var newSelectedNode = this._computeNewSelection(event._treeCurrentNode);
      if (newSelectedNode) {
        return newSelectedNode.$node;
      }
    }.bind(this);
  }


  _computeNewSelection(currentNode) {
    var nodes = this.field.visibleNodesFlat;
    if (nodes.length === 0) {
      return;
    }
    if (!currentNode) {
      return nodes[0];
    }
    return nodes[nodes.indexOf(currentNode) + 1];
  }
}
