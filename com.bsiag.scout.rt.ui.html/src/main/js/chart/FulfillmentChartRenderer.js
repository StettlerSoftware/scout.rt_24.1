/*
 * Copyright (c) 2014-2020 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the BSI CRM Software License v1.0
 * which accompanies this distribution as bsi-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
import {objects, scout} from '@eclipse-scout/core';
import {AbstractCircleChartRenderer} from '../index';
import $ from 'jquery';

export default class FulfillmentChartRenderer extends AbstractCircleChartRenderer {

  constructor(chart) {
    super(chart);

    this.segmentSelectorForAnimation = '.fulfillment-chart';
    this.suppressLegendBox = true;

    let defaultConfig = {
      fulfillment: {
        startValue: undefined
      }
    };
    chart.config = $.extend(true, {}, defaultConfig, chart.config);
  }

  _validate() {
    let chartValueGroups = this.chart.data.chartValueGroups;
    if (chartValueGroups.length !== 2 ||
      chartValueGroups[0].values.length !== 1 ||
      chartValueGroups[1].values.length !== 1) {
      return false;
    }
    return true;
  }

  _renderInternal() {
    // Calculate percentage
    let chartData = this.chart.data;
    let value = chartData.chartValueGroups[0].values[0];
    let total = chartData.chartValueGroups[1].values[0];

    this.fullR = (Math.min(this.chartBox.height, this.chartBox.width) / 2) - 2;

    this._renderInnerCircle();
    this._renderPercentage(value, total);
    this._renderLegendEntry(chartData.chartValueGroups[0].groupName, null, null, 0);
  }

  _renderPercentage(value, total) {
    // arc segment
    let arcClass = 'fulfillment-chart',
      color = this.chart.data.chartValueGroups[0].colorHexValue,
      chartGroupCss = this.chart.data.chartValueGroups[0].cssClass;

    if (this.chart.config.options.autoColor) {
      arcClass += ' auto-color';
    } else if (chartGroupCss) {
      arcClass += ' ' + chartGroupCss;
    }

    let startValue = scout.nvl(this.chart.config.fulfillment.startValue, 0);
    let end = 0;
    let lastEnd = 0;
    if (total) {
      // use slightly less than 1.0 as max value because otherwise, the SVG element would not be drawn correctly.
      end = Math.min(value / total, 0.999999);
      lastEnd = Math.min(startValue / total, 0.999999);
    }
    this.r = this.fullR;

    let that = this;
    let tweenInFunc = function(now, fx) {
      let $this = $(this);
      let start = $this.data('animation-start'),
        end = $this.data('animation-end');
      $this.attr('d', that.pathSegment(start * fx.pos, lastEnd + (end - lastEnd) * fx.pos));
    };

    let $arc = this.$svg.appendSVG('path', arcClass)
      .data('animation-start', 0)
      .data('animation-end', end);

    let radius2 = (this.fullR / 8) * 6.7;
    this._renderCirclePath('fulfillment-chart-inner-circle-transparent', 'InnerCircle3', radius2);

    // Label
    let percentage = (total ? Math.round((value / total) * 100) : 0);
    let $label = this.$svg.appendSVG('text', 'fulfillment-chart-label ')
      .attr('x', this.chartBox.mX())
      .attr('y', this.chartBox.mY())
      .css('font-size', (this.fullR / 2) + 'px') // font of label in center relative to circle radius
      .attr('dy', '0.3em') // workaround for 'dominant-baseline: central' which is not supported in IE
      .attrXLINK('href', '#InnerCircle')
      .text(percentage + '%');

    if (this.chart.config.options.clickable) {
      $arc.on('click', this._createClickObject(null, null), this.chart._onValueClick.bind(this.chart));
    }
    if (!this.chart.config.options.autoColor && !chartGroupCss) {
      $arc.attr('fill', color);
    }
    if (this.animationDuration) {
      $arc
        .attr('d', this.pathSegment(0, lastEnd))
        .animate({
          tabIndex: 0
        }, this._createAnimationObjectWithTabindexRemoval(tweenInFunc, this.animationDuration));

      $label
        .attr('opacity', 0)
        .animateSVG('opacity', 1, this.animationDuration, null, true);
    } else {
      $arc.attr('d', this.pathSegment(0, end));
    }
  }

  _useFontSizeBig() {
    return false;
  }

  _renderCirclePath(cssClass, id, radius) {
    let chartGroupCss = this.chart.data.chartValueGroups[0].cssClass;
    let color = this.chart.data.chartValueGroups[1].colorHexValue;

    if (this.chart.config.options.autoColor) {
      cssClass += ' auto-color';
    } else if (chartGroupCss) {
      cssClass += ' ' + chartGroupCss;
    }

    let radius2 = radius * 2;
    let $path = this.$svg.appendSVG('path', cssClass)
      .attr('id', id)
      .attr('d', 'M ' + this.chartBox.mX() + ' ' + this.chartBox.mY() +
        ' m 0, ' + (-radius) +
        ' a ' + radius + ',' + radius + ' 0 1, 1 0,' + radius2 +
        ' a ' + radius + ',' + radius + ' 0 1, 1 0,' + (-radius2));

    if (!this.chart.config.options.autoColor && !chartGroupCss) {
      $path
        .attr('fill', color)
        .attr('stroke', color);
    }

    return $path;
  }

  _renderInnerCircle() {
    let radius = (this.fullR / 8) * 7.5,
      radius2 = (this.fullR / 8) * 7.2;

    this._renderCirclePath('fulfillment-chart-inner-circle', 'InnerCircle', radius);
    this._renderCirclePath('fulfillment-chart-inner-circle-transparent', 'InnerCircle2', radius2);
  }

  /**
   * Do not animate the removal of the chart if the chart data has been updated and the startValue option is set.
   * If startValue is not set use default implementation.
   */
  shouldAnimateRemoveOnUpdate(opts) {
    let startValue = objects.optProperty(this.chart, 'config', 'fulfillment', 'startValue');
    if (!objects.isNullOrUndefined(startValue)) {
      return false;
    }

    return super.shouldAnimateRemoveOnUpdate(opts);
  }
}
