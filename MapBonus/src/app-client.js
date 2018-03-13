import 'babel-polyfill';
import 'babel-register';

import React from 'react';
import ReactDOM from 'react-dom';
import {Provider} from 'react-redux';

import AppState from './reducers';
import Home from './home.js'

ReactDOM.render(
  <Provider store={AppState} >
    <Home />
  </Provider>,
  document.getElementById('map')
);
