import React, {Component} from 'react';

import ViewportAnimation from './utils/map-utils';
import TripsOverlay from './trips/deckgl-overlay';

import {MAPBOX_STYLES} from './constants/defaults';

export default class TripsDemo extends Component {

  static get data() {
    return [
      {
        url: 'data/trips-data.txt',
        worker: 'workers/trips-data-decoder.js?loop=1800&trail=180'
      },
      {
        url: 'data/building-data.txt',
        worker: 'workers/building-data-decoder.js'
      }
    ];
  }

  static get parameters() {
    return {
      trail: {displayName: 'Trail', type: 'range', value: 180, step: 1, min: 10, max: 200}
    };
  }

  static get viewport() {
    return {
      ...TripsOverlay.defaultViewport,
      mapStyle: MAPBOX_STYLES.DARK,
      startDragLngLat: null,
      isDragging: true,
    };
  }

  constructor(props) {
    super(props);

    this.state = {time: 0};

    const thisDemo = this; // eslint-disable-line

    this.tween = ViewportAnimation.ease({time: 0}, {time: 1800}, 60000)
      .onUpdate(function tweenUpdate() {
        thisDemo.setState(this); // eslint-disable-line
      })
      .repeat(Infinity);
  }

  componentDidMount() {
    this.tween.start();
  }

  componentWillUnmount() {
    this.tween.stop();
  }

  render() {
    const {viewport, data, params} = this.props;

    if (!data) {
      return null;
    }

    return (
      <TripsOverlay viewport={viewport}
        trips={data[0]}
        buildings={data[1]}
        trailLength={params.trail.value}
        time={this.state.time} />
    );
  }
}
