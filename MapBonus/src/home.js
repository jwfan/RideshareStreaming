/* global window */
import React, {Component} from 'react';
import {connect} from 'react-redux';

import Stats from 'stats.js';

import {updateMap, setHeaderOpacity} from './actions/app-actions';

import Map from './map';

class Home extends Component {
  constructor(props) {
    super(props);
    this.state = {};
  }

  componentDidMount() {
    window.onscroll = this._onScroll.bind(this);
    window.onresize = this._resizeMap.bind(this);
    this._onScroll();
    this._resizeMap();

    this._stats = new Stats();
    this._stats.showPanel(0);
    this.refs.fps.appendChild(this._stats.dom);

    const calcFPS = () => {
      this._stats.begin();
      this._stats.end();
      this._animateRef = window.requestAnimationFrame(calcFPS);
    };

    this._animateRef = window.requestAnimationFrame(calcFPS);
  }

  componentWillUnmount() {
    window.onscroll = null;
    window.onresize = null;
    window.cancelAnimationFrame(this._animateRef);
  }

  _resizeMap() {
    const container = this.refs.banner;
    const width = container.clientWidth;
    const height = container.clientHeight;
    this.props.updateMap({width, height});
  }

  _onScroll() {
    const y = window.pageYOffset;
    const opacity = Math.max(0, Math.min(1, (y - 168) / 20));
    this.props.setHeaderOpacity(opacity);
  }

  render() {
    const {atTop} = this.state;
    return (
      <div className={`home-wrapper ${atTop ? 'top' : ''}`}>

        <section ref="banner" id="banner">
          <div className="hero">
            <Map demo="HomeDemo" isInteractive={true} />
          </div>
          <div ref="fps" className="fps" />
        </section>

      </div>
    );
  }
}

export default connect(
  state => ({}),
  {updateMap, setHeaderOpacity}
)(Home);
