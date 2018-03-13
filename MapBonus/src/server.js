/* eslint no-console: "off"*/

import path from 'path';
import { Server } from 'http';
import Express from 'express';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { StaticRouter as Router } from 'react-router-dom';
import {Provider} from 'react-redux';
import AppState from './reducers';
import Home from './home.js'

const app = new Express();
const server = new Server(app);

// You may use any Kafka package (kafka-node, no-kafka...) you wish to read
// the message from the topic and render them on the client.

// REPLACE HOST NAME
var kafka = require('kafka-node'),
    Consumer = kafka.Consumer,
    client   = new kafka.Client("ec2-52-91-225-187.compute-1.amazonaws.com:2181"),
    consumer = new Consumer(
      client,
      [
        { topic: 'driver-locations', partition: 0 }
      ],
      {
        autoCommit: false
      }
    );

consumer.on('message', function(message) {
    console.log(message);
});

// use ejs templates
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// define the folder that will be used for static assets
app.use(Express.static(path.join(__dirname, 'static')));

// universal routing and rendering
app.get('*', (req, res) => {
  let markup = '';
  let status = 200;

  if (process.env.UNIVERSAL) {
    const context = {};
    markup = renderToString(
      <Provider store={AppState} >
        <Home />
      </Provider>,
    );

    // context.url will contain the URL to redirect to if a <Redirect> was used
    if (context.url) {
      return res.redirect(302, context.url);
    }

    if (context.is404) {
      status = 404;
    }
  }

  return res.status(status).render('index', { markup });
});

// start the server
const port = process.env.PORT || 3000;
const env = process.env.NODE_ENV || 'production';
server.listen(port, "0.0.0.0", (err) => {
  if (err) {
    return console.error(err);
  }
  return console.info(
    `
      Server running on http://0.0.0.0:${port} [${env}]
      Universal rendering: ${process.env.UNIVERSAL ? 'enabled' : 'disabled'}
    `);
});
