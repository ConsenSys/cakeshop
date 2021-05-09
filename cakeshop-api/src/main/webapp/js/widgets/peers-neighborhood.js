import utils from '../utils';

module.exports = function() {
	var extended = {
		name: 'peers-neighborhood',
		title: 'Peer Neighborhood',
		size: 'small',
		knownPeers: [],
		ip: null,

		hideLink: true,

		url: 'api/node/add_peer',

		template: _.template('<table style="width: 100%; table-layout: fixed;" class="table table-striped">' +
				 '<thead style="font-weight: bold;"><tr><td>Neighbor</td><td style="width: 50px;">Blocks</td><td style="width: 50px;">Add</td></tr></thead>' +
				 '<tbody></tbody></table>'),

		templateRow: _.template('<tr><td><span style="font-size: smaller"><%= nodeName %> [<%= nodeIP %>]</span></td><td><%= latestBlock %></td><td><a class="btn btn-primary btn-sm" href="#" data-enode="<%= nodeUrl %>" id="neighbor-add"><i class="fa fa-plus"></i></a></td></tr>'),

		setData: function(data) {
			this.data = data;

			this.ip = data;
		},

		subscribe: function(data) {
			// adding listener to add knownPeers
			Dashboard.Utils.on(function(ev, action) {
				if (action.indexOf('peers-list|fetch|') === 0) {
					widget.knownPeers = [];

					var peers = JSON.parse(action.replace('peers-list|fetch|', ''));

					_.each(peers, function(peer) {
						widget.knownPeers.push(peer.nodeIP);
					});
				}
			});
		},

		fetch: function() {
			$('#widget-' + this.shell.id + ' > table > tbody').empty();

			var hood = [], _this = this;

			this.ip.split(',').forEach(function(ip) {
				var last = ip.split('.').splice(3),
					split = ip.split('.').splice(0, 3).join('.');

				_.each(_.range(1, 256), function(i) {
					var ip = split + '.' + i;

					if (last == i) {
						return;
					} else if (_.indexOf(widget.knownPeers, ip) >= 0) {
						return;
					}

					hood.push(ip);
				});
			});


			_.each(hood, function(ip) {
				var port = (window.location.port ? ':' + window.location.port : ''),
					ep = window.location.protocol + '//' + ip + port + window.location.pathname + 'ws',
					stomp = Stomp.over(new SockJS(ep));

				stomp.debug = null;

				stomp.connect({}, function(frame) {
					// Connection successful
					stomp.subscribe('/topic/node/status', function(res) {
						var status = JSON.parse(res.body);
						status = status.data.attributes;

						stomp.disconnect();

						widget.showNeighbor(status);
					});
				});

				_this.postFetch();
			});
		},

		render: function() {
			Dashboard.render.widget(this.name, this.shell.tpl);

			$('#widget-' + this.shell.id).css({ 'height': '240px', 'margin-bottom': '10px', 'overflow-x': 'hidden', 'width': '100%' });
			$('#widget-' + this.shell.id).html( this.template() );
			$('#widget-' + this.shell.id + ' > table > tbody').on('click', '#neighbor-add', this._handler);

			this.fetch();
		},

		_handler: function(e) {
			e.preventDefault();

			var nodeUrl = $(this).data('enode'), _this = $(this);

			$.when(
				utils.load({ url: widget.url, data: { "args": nodeUrl } })
			).done(function(r) {
				setTimeout(function() {
					_this.fadeOut();
				}, 1000);
			});
		},

		showNeighbor: function(status) {
			if (!status || !status.nodeIP || !status.nodeUrl)
				return;

			if (_.indexOf(widget.knownPeers, status.nodeIP) >= 0) {
				return;
			}

			status.nodeIP.split(',').forEach(function(ip) {
				var url = status.nodeUrl.replace(/@[\d.]+/, "@" + ip); // replace correct IP in url
				var ctx = {
					nodeName: status.nodeName,
					nodeIP: ip,
					nodeUrl: url,
					latestBlock: status.latestBlock
				};
				$('#widget-' + widget.shell.id + ' > table > tbody').append( widget.templateRow(ctx) );
			});
		}
	};


	var widget = _.extend({}, widgetRoot, extended);

	// register presence with screen manager
	Dashboard.addWidget(widget);
};
